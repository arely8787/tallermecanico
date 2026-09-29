package mx.taller.api.client

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
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
  val branchCode: String?
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
  val branchCode: String?,
  val photoUrl: String
)

data class ClientPhoto(val bytes: ByteArray, val contentType: String)

@Service
class ClientFacade(private val clients: ClientRepository) {
  @Transactional
  fun create(command: ClientCommand, photo: MultipartFile, actorId: Long): ClientView {
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
    return NormalizedClient(
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

  private fun Client.toView() = ClientView(id!!, fullName, alternativeContactName, age, birthDate, phone, occupation, email, street, neighborhood, municipality, state, postalCode, branchCode, "/api/clientes/$id/foto")

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
  }
}
