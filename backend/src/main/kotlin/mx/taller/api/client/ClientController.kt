package mx.taller.api.client

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

class ClientRequest {
  @field:NotBlank @field:Size(max = 150) var fullName: String = ""
  @field:Size(max = 150) var alternativeContactName: String? = null
  @field:NotNull @field:Min(0) @field:Max(130) var age: Int? = null
  @field:NotNull @field:Past @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE) var birthDate: LocalDate? = null
  @field:NotBlank @field:Size(max = 25) var phone: String = ""
  @field:NotBlank @field:Size(max = 150) var occupation: String = ""
  @field:NotBlank @field:Email @field:Size(max = 150) var email: String = ""
  @field:NotBlank @field:Size(max = 150) var street: String = ""
  @field:NotBlank @field:Size(max = 100) var neighborhood: String = ""
  @field:NotBlank @field:Size(max = 100) var municipality: String = ""
  @field:NotBlank @field:Size(max = 100) var state: String = ""
  @field:Pattern(regexp = "\\d{5}") var postalCode: String = ""
  @field:Pattern(regexp = "[ABab]") var branchCode: String? = null

  fun toCommand() = ClientCommand(fullName, alternativeContactName, age!!, birthDate!!, phone, occupation, email, street, neighborhood, municipality, state, postalCode, branchCode)
}

@RestController
@RequestMapping("/api/clientes")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
class ClientController(private val facade: ClientFacade) {
  @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun create(@Valid @ModelAttribute request: ClientRequest, @RequestPart("photo") photo: MultipartFile, authentication: Authentication): ResponseEntity<ClientView> =
    ResponseEntity.status(201).body(facade.create(request.toCommand(), photo, actorId(authentication)))

  @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun update(@PathVariable id: Long, @Valid @ModelAttribute request: ClientRequest, @RequestPart("photo", required = false) photo: MultipartFile?, authentication: Authentication): ClientView =
    facade.update(id, request.toCommand(), photo, actorId(authentication))

  @GetMapping
  fun list(): List<ClientView> = facade.list()

  @GetMapping("/{id}/foto")
  fun photo(@PathVariable id: Long): ResponseEntity<ByteArray> {
    val photo = facade.photo(id)
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType)).body(photo.bytes)
  }

  private fun actorId(authentication: Authentication): Long = authentication.principal as? Long ?: throw IllegalStateException("Usuario autenticado inválido")
}
