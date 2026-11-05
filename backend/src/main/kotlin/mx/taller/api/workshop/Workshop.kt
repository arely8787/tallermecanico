package mx.taller.api.workshop

import jakarta.persistence.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import mx.taller.api.client.ClientRepository
import mx.taller.api.postal.PostalCodeFacade
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.Locale

@Entity
@Table(name = "workshops", uniqueConstraints = [
  UniqueConstraint(name = "uk_workshop_rfc", columnNames = ["rfc"]),
  UniqueConstraint(name = "uk_workshop_legal_name", columnNames = ["legal_name_normalized"]),
  UniqueConstraint(name = "uk_workshop_phone", columnNames = ["phone_normalized"]),
  UniqueConstraint(name = "uk_workshop_email", columnNames = ["email_normalized"])
])
class Workshop(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @Column(nullable = false, length = 150) var name: String = "",
  @Column(name = "legal_name", nullable = false, length = 180) var legalName: String = "",
  @Column(name = "legal_name_normalized", nullable = false, length = 180) var legalNameNormalized: String = "",
  @Column(nullable = false, length = 13) var rfc: String = "",
  @Column(nullable = false, length = 25) var phone: String = "",
  @Column(name = "phone_normalized", nullable = false, length = 15) var phoneNormalized: String = "",
  @Column(name = "personal_phone", length = 25) var personalPhone: String? = null,
  @Column(nullable = false, length = 150) var email: String = "",
  @Column(name = "email_normalized", nullable = false, length = 150) var emailNormalized: String = "",
  @Column(nullable = false, length = 150) var street: String = "",
  @Column(nullable = false, length = 100) var neighborhood: String = "",
  @Column(nullable = false, length = 100) var municipality: String = "",
  @Column(nullable = false, length = 100) var state: String = "",
  @Column(name = "postal_code", nullable = false, length = 5) var postalCode: String = "",
  @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'ACTIVO'") var status: String = "ACTIVO",
  @Lob @Column(name = "photo_content", nullable = false, columnDefinition = "LONGBLOB") var photoContent: ByteArray = byteArrayOf(),
  @Column(name = "photo_content_type", nullable = false, length = 20) var photoContentType: String = ""
)

interface WorkshopRepository : org.springframework.data.jpa.repository.JpaRepository<Workshop, Long> {
  fun existsByRfcAndIdNot(rfc: String, id: Long): Boolean
  fun existsByLegalNameNormalizedAndIdNot(legalNameNormalized: String, id: Long): Boolean
  fun existsByPhoneNormalizedAndIdNot(phoneNormalized: String, id: Long): Boolean
  fun existsByEmailNormalizedAndIdNot(emailNormalized: String, id: Long): Boolean
  fun findAllByStatusOrderByNameAsc(status: String): List<Workshop>
}

class WorkshopRequest {
  @field:NotBlank @field:Size(max = 150) var name: String = ""
  @field:NotBlank @field:Size(max = 180) var legalName: String = ""
  @field:NotBlank @field:Size(max = 13) var rfc: String = ""
  @field:NotBlank @field:Size(max = 25) var phone: String = ""
  @field:NotBlank @field:Size(max = 25) var personalPhone: String = ""
  @field:NotBlank @field:Email @field:Size(max = 150) var email: String = ""
  @field:NotBlank @field:Size(max = 150) var street: String = ""
  @field:NotBlank @field:Size(max = 100) var neighborhood: String = ""
  @field:NotBlank @field:Size(max = 100) var municipality: String = ""
  @field:NotBlank @field:Size(max = 100) var state: String = ""
  @field:Pattern(regexp = "\\d{5}") var postalCode: String = ""
}

data class WorkshopView(val id: Long, val name: String, val legalName: String, val rfc: String, val phone: String, val personalPhone: String?, val email: String, val street: String, val neighborhood: String, val municipality: String, val state: String, val postalCode: String, val status: String)

@Service
class WorkshopFacade(private val workshops: WorkshopRepository, private val postalCodes: PostalCodeFacade, private val clients: ClientRepository) {
  @Transactional fun create(request: WorkshopRequest, photo: MultipartFile): WorkshopView {
    return save(Workshop(), request, photo, requiredPhoto = true)
  }

  @Transactional fun update(id: Long, request: WorkshopRequest, photo: MultipartFile?): WorkshopView {
    val workshop = workshops.findById(id).orElseThrow { notFound() }
    return save(workshop, request, photo, requiredPhoto = false)
  }

  @Transactional fun toggleStatus(id: Long): WorkshopView {
    val workshop = workshops.findById(id).orElseThrow { notFound() }
    workshop.status = if (workshop.status == ACTIVE) SUSPENDED else ACTIVE
    return workshops.saveAndFlush(workshop).view()
  }

  @Transactional fun delete(id: Long) {
    val workshop = workshops.findById(id).orElseThrow { notFound() }
    if (clients.countByWorkshopId(id) > 0) throw ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar un taller con clientes asociados; considera suspenderlo")
    workshop.photoContent = byteArrayOf()
    workshops.delete(workshop)
  }

  private fun save(workshop: Workshop, request: WorkshopRequest, photo: MultipartFile?, requiredPhoto: Boolean): WorkshopView {
    val rfc = request.rfc.trim().uppercase(Locale.ROOT)
    val phoneNormalized = request.phone.filter(Char::isDigit)
    val personalPhone = required(request.personalPhone, "El teléfono personal es obligatorio")
    val personalPhoneNormalized = personalPhone.filter(Char::isDigit)
    val email = request.email.trim().lowercase(Locale.ROOT)
    val legalName = required(request.legalName, "La razón social es obligatoria")
    if (!RFC.matches(rfc)) invalid("El RFC no tiene un formato válido")
    if (phoneNormalized.length !in 7..15) invalid("El teléfono no tiene un formato válido")
    if (personalPhoneNormalized.length !in 7..15) invalid("El teléfono personal no tiene un formato válido")
    if (!EMAIL.matches(email)) invalid("El correo no tiene un formato válido")
    val id = workshop.id ?: -1L
    if (workshops.existsByRfcAndIdNot(rfc, id) || workshops.existsByLegalNameNormalizedAndIdNot(normalize(legalName), id) || workshops.existsByPhoneNormalizedAndIdNot(phoneNormalized, id) || workshops.existsByEmailNormalizedAndIdNot(email, id)) duplicate()
    val postalCode = request.postalCode.trim()
    postalCodes.validateAddress(postalCode, request.state, request.municipality, request.neighborhood)
    val image = photo?.takeUnless { it.isEmpty }?.let(::photo) ?: if (requiredPhoto) invalid("La fotografía del taller es obligatoria") else null
    workshop.apply {
      name = required(request.name, "El nombre del taller es obligatorio")
      this.legalName = legalName; legalNameNormalized = normalize(legalName); this.rfc = rfc
      phone = request.phone.trim(); this.phoneNormalized = phoneNormalized; this.personalPhone = personalPhone
      this.email = email; emailNormalized = email; street = required(request.street, "La calle es obligatoria")
      neighborhood = required(request.neighborhood, "La colonia es obligatoria"); municipality = required(request.municipality, "El municipio es obligatorio")
      state = required(request.state, "El estado es obligatorio"); this.postalCode = postalCode
      if (image != null) { photoContent = image.first; photoContentType = image.second }
    }
    return try { workshops.saveAndFlush(workshop).view() } catch (_: DataIntegrityViolationException) { duplicate() }
  }
  @Transactional(readOnly = true) fun list(): List<WorkshopView> = workshops.findAll().map { it.view() }
  @Transactional(readOnly = true) fun listActive(): List<WorkshopView> = workshops.findAllByStatusOrderByNameAsc(ACTIVE).map { it.view() }
  fun requireExists(id: Long) { if (!workshops.existsById(id)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El taller seleccionado no existe") }
  fun requireActive(id: Long) { if (workshops.findById(id).orElse(null)?.status != ACTIVE) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El taller seleccionado no está activo") }
  private fun photo(file: MultipartFile): Pair<ByteArray, String> { if (file.isEmpty) invalid("La fotografía del taller es obligatoria"); if (file.size > 15L * 1024 * 1024) invalid("La fotografía no puede superar 15 MB"); val bytes = file.bytes; return when { bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG) -> bytes to "image/png"; bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> bytes to "image/jpeg"; else -> invalid("La fotografía debe ser JPG o PNG") } }
  private fun Workshop.view() = WorkshopView(id!!, name, legalName, rfc, phone, personalPhone, email, street, neighborhood, municipality, state, postalCode, status)
  private fun required(value: String, message: String) = value.trim().ifBlank { invalid(message) }
  private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
  private fun invalid(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
  private fun duplicate(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un taller con el mismo RFC, razón social, teléfono o correo")
  private fun notFound(): ResponseStatusException = ResponseStatusException(HttpStatus.NOT_FOUND, "Taller no encontrado")
  private companion object { const val ACTIVE = "ACTIVO"; const val SUSPENDED = "SUSPENDIDO"; val RFC = Regex("^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$"); val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"); val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) }
}

@RestController @RequestMapping("/api/talleres") @PreAuthorize("hasRole('ADMIN')")
class WorkshopController(private val facade: WorkshopFacade) {
  @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]) fun create(@Valid @ModelAttribute request: WorkshopRequest, @RequestPart("photo") photo: MultipartFile): ResponseEntity<WorkshopView> = ResponseEntity.status(201).body(facade.create(request, photo))
  @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]) fun update(@PathVariable id: Long, @Valid @ModelAttribute request: WorkshopRequest, @RequestPart("photo", required = false) photo: MultipartFile?): WorkshopView = facade.update(id, request, photo)
  @PatchMapping("/{id}/estatus") fun toggleStatus(@PathVariable id: Long): WorkshopView = facade.toggleStatus(id)
  @DeleteMapping("/{id}") fun delete(@PathVariable id: Long): ResponseEntity<Void> { facade.delete(id); return ResponseEntity.noContent().build() }
  @GetMapping fun list(): List<WorkshopView> = facade.list()
}

@RestController @RequestMapping("/api/talleres/activos") @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
class ActiveWorkshopController(private val facade: WorkshopFacade) {
  @GetMapping fun list(): List<WorkshopView> = facade.listActive()
}
