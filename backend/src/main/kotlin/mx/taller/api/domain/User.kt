package mx.taller.api.domain

import jakarta.persistence.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

enum class Role { ADMIN, GERENTE, AUXILIAR, MECANICO, CLIENTE, RECEPCIONISTA, ALMACENISTA }
enum class UserStatus { PENDING, ACTIVE, SUSPENDED, DISABLED }

@Entity @Table(name = "users")
class User(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @Column(nullable = false) var name: String = "",
  @Column(nullable = false, unique = true) var email: String = "",
  @Column(nullable = false) var passwordHash: String = "",
  @Enumerated(EnumType.STRING) @Column(nullable = false) var role: Role = Role.CLIENTE,
  @Enumerated(EnumType.STRING) @Column(nullable = false) var status: UserStatus = UserStatus.ACTIVE,
  var resetToken: String? = null,
  var resetExpiresAt: Instant? = null
)

interface UserRepository : org.springframework.data.jpa.repository.JpaRepository<User, Long> {
  fun findByEmail(email: String): User?
  fun findByResetToken(resetToken: String): User?
  fun countByRoleAndStatus(role: Role, status: UserStatus): Long
}

@Service
class UserFacade(private val users: UserRepository) {
  @Transactional(readOnly = true)
  fun listEmployees(): List<User> = users.findAll().filter { it.role != Role.CLIENTE }

  @Transactional
  fun toggleStatus(id: Long, actorId: Long): User {
    val user = users.findById(id).orElseThrow { notFound() }
    protectActor(user, actorId)
    if (user.status == UserStatus.ACTIVE && user.role == Role.ADMIN && users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe permanecer al menos un administrador activo")
    }
    user.status = if (user.status == UserStatus.ACTIVE) UserStatus.SUSPENDED else UserStatus.ACTIVE
    return users.saveAndFlush(user)
  }

  @Transactional
  fun delete(id: Long, actorId: Long) {
    val user = users.findById(id).orElseThrow { notFound() }
    protectActor(user, actorId)
    if (user.role == Role.ADMIN && user.status == UserStatus.ACTIVE && users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede eliminar al último administrador activo")
    }
    users.delete(user)
  }

  private fun protectActor(user: User, actorId: Long) { if (user.id == actorId) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes suspender ni eliminar tu propia cuenta") }
  private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado")
}
