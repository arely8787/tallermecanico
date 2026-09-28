package mx.taller.api.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import mx.taller.api.domain.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize
import mx.taller.api.security.TokenService
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

data class RegisterRequest(@field:NotBlank val name: String, @field:Email val email: String, @field:Size(min=8) val password: String)
data class LoginRequest(@field:Email val email: String, @field:NotBlank val password: String)
data class ForgotRequest(@field:Email val email: String)
data class ResetRequest(@field:NotBlank val token: String, @field:Size(min=8) val password: String)
data class AssignRoleRequest(val role: Role, val status: UserStatus = UserStatus.ACTIVE)

@RestController @RequestMapping("/api")
class AuthController(private val users: UserRepository, private val encoder: PasswordEncoder, private val tokens: TokenService) {
  @PostMapping("/auth/register") fun register(@Valid @RequestBody body: RegisterRequest): ResponseEntity<Any> {
    if (users.findByEmail(body.email.lowercase()) != null) return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to "El correo ya está registrado"))
    users.save(User(name=body.name.trim(), email=body.email.lowercase(), passwordHash=encoder.encode(body.password)))
    return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("message" to "Cuenta creada con rol Cliente. Ya puedes iniciar sesión; un gerente puede ajustar tu rol."))
  }
  @PostMapping("/auth/login") fun login(@Valid @RequestBody body: LoginRequest): ResponseEntity<Any> {
    val user = users.findByEmail(body.email.lowercase()) ?: return unauthorized()
    if (!encoder.matches(body.password, user.passwordHash)) return unauthorized()
    if (user.status != UserStatus.ACTIVE) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("message" to "Tu cuenta está pendiente de aprobación o fue deshabilitada."))
    val token=tokens.create(user.id!!, user.role)
    return ResponseEntity.ok(mapOf("token" to token, "user" to mapOf("id" to user.id, "name" to user.name, "email" to user.email, "role" to user.role)))
  }
  @PostMapping("/auth/forgot-password") fun forgot(@Valid @RequestBody body: ForgotRequest): ResponseEntity<Any> {
    val user = users.findByEmail(body.email.lowercase())
    var devToken: String? = null
    if (user != null) { devToken = randomToken(); user.resetToken=devToken; user.resetExpiresAt=Instant.now().plus(30, ChronoUnit.MINUTES); users.save(user) }
    return ResponseEntity.ok(mapOf("message" to "Si existe una cuenta, recibirás instrucciones de recuperación.", "developmentToken" to devToken))
  }
  @PostMapping("/auth/reset-password") fun reset(@Valid @RequestBody body: ResetRequest): ResponseEntity<Any> {
    val user=users.findByResetToken(body.token) ?: return ResponseEntity.badRequest().body(mapOf("message" to "Token inválido o expirado"))
    if (user.resetExpiresAt?.isBefore(Instant.now()) != false) return ResponseEntity.badRequest().body(mapOf("message" to "Token inválido o expirado"))
    user.passwordHash=encoder.encode(body.password); user.resetToken=null; user.resetExpiresAt=null; users.save(user)
    return ResponseEntity.ok(mapOf("message" to "Contraseña actualizada. Ya puedes iniciar sesión."))
  }
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')") @GetMapping("/users") fun list() = users.findAll().map { mapOf("id" to it.id, "name" to it.name, "email" to it.email, "role" to it.role, "status" to it.status) }
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')") @PatchMapping("/users/{id}/role") fun assign(@PathVariable id: Long, @RequestBody body: AssignRoleRequest): ResponseEntity<Any> {
    val user=users.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
    user.role=body.role; user.status=body.status; users.save(user); return ResponseEntity.ok(mapOf("message" to "Rol actualizado"))
  }
  private fun unauthorized(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body<Any>(mapOf("message" to "Correo o contraseña incorrectos"))
  private fun randomToken(): String { val bytes=ByteArray(32); SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) }
}
