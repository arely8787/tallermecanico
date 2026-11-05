package mx.taller.api.status

import jakarta.persistence.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.Locale

@Entity
@Table(name = "status", uniqueConstraints = [UniqueConstraint(name = "uk_status_strvalor", columnNames = ["strvalor"])])
class Status(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @Column(name = "strvalor", nullable = false, length = 30) var value: String = "",
  @Column(name = "strdescripcion", nullable = false, length = 255) var description: String = ""
)

interface StatusRepository : org.springframework.data.jpa.repository.JpaRepository<Status, Long> {
  fun findByValue(value: String): Status?
  fun existsByValueAndIdNot(value: String, id: Long): Boolean
}

data class StatusView(val id: Long, val strvalor: String, val strdescripcion: String)
data class StatusPageView(val data: List<StatusView>, val page: Int, val totalPages: Int, val totalRecords: Long)
data class StatusRequest(@field:NotBlank @field:Size(max = 30) val strvalor: String, @field:NotBlank @field:Size(max = 255) val strdescripcion: String)

@Service
class StatusFacade(private val statuses: StatusRepository) {
  @Transactional(readOnly = true)
  fun page(page: Int, size: Int): StatusPageView {
    if (page < 0 || size !in 1..20) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La página o tamaño solicitado no es válido")
    val result = statuses.findAll(PageRequest.of(page, size))
    return StatusPageView(result.content.map { it.view() }, result.number, result.totalPages, result.totalElements)
  }

  @Transactional
  fun create(request: StatusRequest): StatusView {
    val value = value(request.strvalor)
    if (statuses.findByValue(value) != null) duplicate()
    return statuses.saveAndFlush(Status(value = value, description = description(request.strdescripcion))).view()
  }

  @Transactional
  fun update(id: Long, request: StatusRequest): StatusView {
    val status = statuses.findById(id).orElseThrow { notFound() }
    val value = value(request.strvalor)
    if (status.value in BASE_VALUES && value != status.value) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor de los estatus base no se puede modificar")
    if (statuses.existsByValueAndIdNot(value, id)) duplicate()
    status.value = value
    status.description = description(request.strdescripcion)
    return statuses.saveAndFlush(status).view()
  }

  @Transactional(readOnly = true)
  fun required(value: String): Status = statuses.findByValue(value)
    ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No está configurado el estatus $value")

  private fun value(value: String): String = value.trim().uppercase(Locale.ROOT).also {
    if (!Regex("^[A-ZÑ]+$").matches(it)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor del estatus debe estar en mayúsculas y contener solo letras")
  }
  private fun description(value: String) = value.trim().ifBlank { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción es obligatoria") }
  private fun duplicate(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un estatus con ese valor")
  private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Estatus no encontrado")
  private fun Status.view() = StatusView(id!!, value, description)
  private companion object { val BASE_VALUES = setOf("ACTIVO", "CANCELADO", "SUSPENDIDO", "ELIMINADO") }
}

@RestController
@RequestMapping("/api/estatus")
@PreAuthorize("hasRole('ADMIN')")
class StatusController(private val facade: StatusFacade) {
  @GetMapping fun list(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "10") size: Int) = facade.page(page, size)
  @PostMapping fun create(@Valid @RequestBody request: StatusRequest) = facade.create(request)
  @PutMapping("/{id}") fun update(@PathVariable id: Long, @Valid @RequestBody request: StatusRequest) = facade.update(id, request)
}
