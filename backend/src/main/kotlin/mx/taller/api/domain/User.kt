package mx.taller.api.domain

import jakarta.persistence.*
import java.time.Instant

enum class Role { ADMIN, GERENTE, MECANICO, CLIENTE, RECEPCIONISTA, ALMACENISTA }
enum class UserStatus { PENDING, ACTIVE, DISABLED }

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
}
