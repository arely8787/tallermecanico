package mx.taller.api.config

import org.springframework.http.ResponseEntity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class ApiError(val message: String)

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException::class)
  fun responseStatus(exception: ResponseStatusException): ResponseEntity<ApiError> =
    ResponseEntity.status(exception.statusCode).body(ApiError(exception.reason ?: "No se pudo completar la operación"))

  @ExceptionHandler(DataIntegrityViolationException::class)
  fun integrity(): ResponseEntity<ApiError> =
    ResponseEntity.status(409).body(ApiError("No se puede eliminar el registro porque tiene información asociada; considera suspenderlo"))
}
