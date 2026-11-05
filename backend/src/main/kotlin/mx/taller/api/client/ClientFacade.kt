package mx.taller.api.client

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import mx.taller.api.postal.PostalCodeFacade
import mx.taller.api.workshop.WorkshopFacade
import mx.taller.api.status.StatusFacade
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.Locale

data class ClientCommand(
  val fullName: String,
  val alternativeContactName: String?,
  val age: Int,
  val birthDate: LocalDate,
  val phone: String,
  val occupation: String,
  val email: String,
  val street: String,
  val neighborhood: String,
  val municipality: String,
  val state: String,
  val postalCode: String,
  val branchCode: String?, val workshopId: Long?
)

data class ClientView(
  val id: Long,
  val fullName: String,
  val alternativeContactName: String?,
  val age: Int,
  val birthDate: LocalDate,
  val phone: String,
  val occupation: String,
  val email: String,
  val street: String,
  val neighborhood: String,
  val municipality: String,
  val state: String,
  val postalCode: String,
  val branchCode: String?, val workshopId: Long?, val status: String,
  val photoUrl: String
)

data class ClientPhoto(val bytes: ByteArray, val contentType: String)
data class ClientPageView(val data: List<ClientView>, val page: Int, val totalPages: Int, val totalRecords: Long)
data class ClientStatusView(val id: Long, val fullName: String, val status: String, val statusDescription: String, val motivoStatus: String?)
data class ClientStatusPageView(val data: List<ClientStatusView>, val page: Int, val totalPages: Int, val totalRecords: Long)

@Service
class ClientFacade(private val clients: ClientRepository, private val postalCodes: PostalCodeFacade, private val workshops: WorkshopFacade, private val statuses: StatusFacade) {
  @Transactional
  fun create(command: ClientCommand, photo: MultipartFile, actorId: Long): ClientView {
    if (command.workshopId == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un taller")
    workshops.requireActive(command.workshopId)
    val normalized = validate(command)
    assertNoDuplicate(normalized)
    val image = validatePhoto(photo, required = true)
    val now = Instant.now()
    return try {
      clients.saveAndFlush(
        Client(
          fullName = normalized.fullName,
          fullNameNormalized = normalized.fullNameNormalized,
          alternativeContactName = normalized.alternativeContactName,
          age = command.age,
          birthDate = command.birthDate,
          phone = normalized.phone,
          phoneNormalized = normalized.phoneNormalized,
          occupation = normalized.occupation,
          email = normalized.email,
          photoContent = image!!.bytes,
          photoContentType = image.contentType,
          street = normalized.street,
          neighborhood = normalized.neighborhood,
          municipality = normalized.municipality,
          state = normalized.state,
          postalCode = normalized.postalCode,
          branchCode = normalized.branchCode,
          workshopId = command.workshopId,
          statusRecord = statuses.required(ACTIVE),
          createdByUserId = actorId,
          updatedByUserId = actorId,
          createdAt = now,
          updatedAt = now
        )
      ).toView()
    } catch (_: DataIntegrityViolationException) {
      duplicate()
    }
  }

  @Transactional
  fun update(id: Long, command: ClientCommand, photo: MultipartFile?, actorId: Long): ClientView {
    val client = clients.findById(id).orElseThrow { notFound() }
    if (command.workshopId != null) workshops.requireActive(command.workshopId)
    val normalized = validate(command)
    assertNoDuplicate(normalized, id)
    val image = validatePhoto(photo, required = false)
    client.apply {
      fullName = normalized.fullName
      fullNameNormalized = normalized.fullNameNormalized
      alternativeContactName = normalized.alternativeContactName
      age = command.age
      birthDate = command.birthDate
      phone = normalized.phone
      phoneNormalized = normalized.phoneNormalized
      occupation = normalized.occupation
      email = normalized.email
      if (image != null) {
        photoContent = image.bytes
        photoContentType = image.contentType
      }
      street = normalized.street
      neighborhood = normalized.neighborhood
      municipality = normalized.municipality
      state = normalized.state
      postalCode = normalized.postalCode
      branchCode = normalized.branchCode
      workshopId = command.workshopId
      updatedByUserId = actorId
      updatedAt = Instant.now()
    }
    return try {
      clients.saveAndFlush(client).toView()
    } catch (_: DataIntegrityViolationException) {
      duplicate()
    }
  }

  @Transactional(readOnly = true)
  fun list(): List<ClientView> = clients.findAll().map { it.toView() }

  @Transactional(readOnly = true)
  fun listActiveForVehicleRegistration(): List<ClientView> = clients.findActiveForVehicleRegistration().map { it.toView() }

  @Transactional(readOnly = true)
  fun get(id: Long): ClientView = clients.findById(id).orElseThrow { notFound() }.toView()

  @Transactional(readOnly = true)
  fun listForAdministration(workshopFilter: String, page: Int, size: Int, direction: String, sortField: String): ClientPageView {
    if (page < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La página debe ser un entero mayor o igual a 0")
    if (size !in 1..10) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El tamaño de página debe estar entre 1 y 10")
    val sortDirection = when (direction.lowercase(Locale.ROOT)) { "asc" -> org.springframework.data.domain.Sort.Direction.ASC; "desc" -> org.springframework.data.domain.Sort.Direction.DESC; else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El orden debe ser asc o desc") }
    if (sortField !in setOf("fullName")) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo de orden no está permitido")
    val pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(sortDirection, sortField))
    val result = if (workshopFilter == "SIN_TALLER") clients.findAdministrationWithoutWorkshop(pageable) else {
      val workshopId = workshopFilter.toLongOrNull()?.takeIf { it > 0 } ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un taller válido")
      workshops.requireExists(workshopId)
      clients.findAdministrationByWorkshopId(workshopId, pageable)
    }
    return ClientPageView(result.content.map { it.toView() }, result.number, result.totalPages, result.totalElements)
  }

  @Transactional(readOnly = true)
  fun listStatusForAdministration(workshopFilter: String, page: Int, size: Int): ClientStatusPageView {
    if (page < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La página debe ser un entero mayor o igual a 0")
    if (size !in 1..10) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El tamaño de página debe estar entre 1 y 10")
    val pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("fullName"))
    val result = if (workshopFilter == "SIN_TALLER") clients.findStatusWithoutWorkshop(pageable) else {
      val workshopId = workshopFilter.toLongOrNull()?.takeIf { it > 0 } ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un taller válido")
      workshops.requireExists(workshopId)
      clients.findStatusByWorkshopId(workshopId, pageable)
    }
    return ClientStatusPageView(result.content.map { ClientStatusView(it.id!!, it.fullName, it.statusValue(), it.statusRecord?.description.orEmpty(), it.statusReason) }, result.number, result.totalPages, result.totalElements)
  }

  @Transactional
  fun suspend(id: Long): ClientView {
    return transition(id, SUSPENDED, null, canDelete = false)
  }

  @Transactional
  fun reactivate(id: Long): ClientView {
    return transition(id, ACTIVE, null, canDelete = false)
  }

  @Transactional
  fun delete(id: Long) {
    transition(id, DELETED, null, canDelete = true)
  }

  @Transactional
  fun changeStatus(id: Long, targetStatus: String, reason: String?, canDelete: Boolean): ClientView = transition(id, targetStatus, reason, canDelete)

  private fun transition(id: Long, targetStatus: String, reason: String?, canDelete: Boolean): ClientView {
    val client = clients.findById(id).orElseThrow { notFound() }
    val target = targetStatus.trim().uppercase(Locale.ROOT)
    when (target) {
      SUSPENDED -> if (client.statusValue() != ACTIVE) throw ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede suspender un cliente con estatus ACTIVO")
      ACTIVE -> if (client.statusValue() != SUSPENDED) throw ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede reactivar un cliente con estatus SUSPENDIDO")
      DELETED -> {
        if (!canDelete) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede eliminar clientes")
        if (client.statusValue() == DELETED) throw ResponseStatusException(HttpStatus.CONFLICT, "El cliente ya está eliminado")
      }
      else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El estatus seleccionado no está permitido")
    }
    client.statusRecord = statuses.required(target)
    if (reason != null) client.statusReason = reason.trim().ifBlank { null }
    return clients.saveAndFlush(client).toView()
  }

  @Transactional(readOnly = true)
  fun photo(id: Long): ClientPhoto {
    val client = clients.findById(id).orElseThrow { notFound() }
    return ClientPhoto(client.photoContent, client.photoContentType)
  }

  private fun assertNoDuplicate(data: NormalizedClient, ignoredId: Long? = null) {
    val match = listOfNotNull(
      clients.findByEmail(data.email),
      clients.findByPhoneNormalized(data.phoneNormalized),
      clients.findByFullNameNormalizedAndBirthDate(data.fullNameNormalized, data.birthDate)
    ).firstOrNull { it.id != ignoredId }
    if (match != null) duplicate()
  }

  private fun validate(command: ClientCommand): NormalizedClient {
    val today = LocalDate.now()
    if (command.birthDate.isAfter(today) || Period.between(command.birthDate, today).years != command.age) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La edad no coincide con la fecha de nacimiento")
    }
    val phoneNormalized = command.phone.filter(Char::isDigit)
    if (phoneNormalized.length !in 7..15) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono no tiene un formato válido")
    val email = command.email.trim().lowercase(Locale.ROOT)
    if (!EMAIL.matches(email)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo no tiene un formato válido")
    val postalCode = command.postalCode.trim()
    if (!POSTAL_CODE.matches(postalCode)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El código postal debe contener 5 dígitos")
    val branch = command.branchCode?.trim()?.uppercase(Locale.ROOT)?.ifBlank { null }
    if (branch != null && branch !in setOf("A", "B")) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La sucursal debe ser A o B")
    val normalized = NormalizedClient(
      fullName = required(command.fullName, "El nombre completo es obligatorio"),
      fullNameNormalized = normalize(command.fullName),
      alternativeContactName = command.alternativeContactName?.trim()?.ifBlank { null },
      phone = command.phone.trim(),
      phoneNormalized = phoneNormalized,
      occupation = required(command.occupation, "El trabajo es obligatorio"),
      email = email,
      street = required(command.street, "La calle es obligatoria"),
      neighborhood = required(command.neighborhood, "La colonia es obligatoria"),
      municipality = required(command.municipality, "El municipio es obligatorio"),
      state = required(command.state, "El estado es obligatorio"),
      postalCode = postalCode,
      branchCode = branch,
      birthDate = command.birthDate
    )
    postalCodes.validateAddress(postalCode, normalized.state, normalized.municipality, normalized.neighborhood)
    return normalized
  }

  private fun validatePhoto(photo: MultipartFile?, required: Boolean): ClientPhoto? {
    if (photo == null || photo.isEmpty) {
      if (required) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La fotografía es obligatoria")
      return null
    }
    if (photo.size > MAX_PHOTO_BYTES) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La fotografía no puede superar 20 MB")
    val bytes = photo.bytes
    val isPng = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)
    val isJpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    return when {
      isPng -> ClientPhoto(bytes, "image/png")
      isJpeg -> ClientPhoto(bytes, "image/jpeg")
      else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La fotografía debe ser JPG o PNG")
    }
  }

  private fun required(value: String, message: String): String = value.trim().ifBlank { throw ResponseStatusException(HttpStatus.BAD_REQUEST, message) }
  private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
  private fun duplicate(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "Posible cliente duplicado: correo, teléfono o nombre y fecha de nacimiento ya existen")
  private fun notFound(): ResponseStatusException = ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado")

  private fun Client.statusValue() = statusRecord?.value ?: ACTIVE
  private fun Client.toView() = ClientView(id!!, fullName, alternativeContactName, age, birthDate, phone, occupation, email, street, neighborhood, municipality, state, postalCode, branchCode, workshopId, statusValue(), "/api/clientes/$id/foto")

  private data class NormalizedClient(
    val fullName: String, val fullNameNormalized: String, val alternativeContactName: String?, val phone: String,
    val phoneNormalized: String, val occupation: String, val email: String, val street: String,
    val neighborhood: String, val municipality: String, val state: String, val postalCode: String,
    val branchCode: String?, val birthDate: LocalDate
  )

  private companion object {
    val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    val POSTAL_CODE = Regex("^\\d{5}$")
    val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
    const val ACTIVE = "ACTIVO"
    const val SUSPENDED = "SUSPENDIDO"
    const val DELETED = "ELIMINADO"
  }
}
