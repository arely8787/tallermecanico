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

data class ClientStatusChangeRequest(
  @field:Pattern(regexp = "ACTIVO|SUSPENDIDO|ELIMINADO") val status: String,
  @field:Size(max = 1000) val motivoStatus: String? = null
)

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
  var workshopId: Long? = null

  fun toCommand() = ClientCommand(fullName, alternativeContactName, age!!, birthDate!!, phone, occupation, email, street, neighborhood, municipality, state, postalCode, branchCode, workshopId)
}

@RestController
@RequestMapping("/api/clientes")
@PreAuthorize("@accessPolicy.canManageClients(authentication)")
class ClientController(private val facade: ClientFacade) {
  @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun create(@Valid @ModelAttribute request: ClientRequest, @RequestPart("photo") photo: MultipartFile, authentication: Authentication): ResponseEntity<ClientView> =
    ResponseEntity.status(201).body(facade.create(request.toCommand(), photo, actorId(authentication)))

  @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun update(@PathVariable id: Long, @Valid @ModelAttribute request: ClientRequest, @RequestPart("photo", required = false) photo: MultipartFile?, authentication: Authentication): ClientView =
    facade.update(id, request.toCommand(), photo, actorId(authentication))

  @GetMapping
  fun list(): List<ClientView> = facade.list()

  @GetMapping("/activos")
  fun listActive(): List<ClientView> = facade.listActiveForVehicleRegistration()

  @GetMapping("/{id}")
  fun get(@PathVariable id: Long): ClientView = facade.get(id)

  @GetMapping("/{id}/foto")
  fun photo(@PathVariable id: Long): ResponseEntity<ByteArray> {
    val photo = facade.photo(id)
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType)).body(photo.bytes)
  }

  private fun actorId(authentication: Authentication): Long = authentication.principal as? Long ?: throw IllegalStateException("Usuario autenticado inválido")
}

@RestController
@RequestMapping("/api/administracion/clientes")
@PreAuthorize("@accessPolicy.canManageClients(authentication)")
class ClientAdministrationController(private val facade: ClientFacade, private val accessPolicy: mx.taller.api.security.AccessPolicy) {
  @GetMapping
  fun list(@RequestParam tallerId: String, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "10") size: Int, @RequestParam(defaultValue = "asc") order: String, @RequestParam(defaultValue = "fullName") sort: String): ClientPageView = facade.listForAdministration(tallerId, page, size, order, sort)

  @PatchMapping("/{id}/suspender") fun suspend(@PathVariable id: Long): ClientView = facade.suspend(id)
  @PatchMapping("/{id}/reactivar") fun reactivate(@PathVariable id: Long): ClientView = facade.reactivate(id)
  @DeleteMapping("/{id}") @PreAuthorize("@accessPolicy.isAdmin(authentication)") fun delete(@PathVariable id: Long): ResponseEntity<Void> { facade.delete(id); return ResponseEntity.noContent().build() }
  @GetMapping("/estatus") fun statusList(@RequestParam tallerId: String, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "10") size: Int): ClientStatusPageView = facade.listStatusForAdministration(tallerId, page, size)
  @PatchMapping("/{id}/estatus") fun changeStatus(@PathVariable id: Long, @Valid @RequestBody request: ClientStatusChangeRequest, authentication: Authentication): ClientView =
    facade.changeStatus(id, request.status, request.motivoStatus, accessPolicy.isAdmin(authentication))
}
