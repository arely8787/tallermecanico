package mx.taller.api.client

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
  name = "clients",
  uniqueConstraints = [
    UniqueConstraint(name = "uk_client_email", columnNames = ["email"]),
    UniqueConstraint(name = "uk_client_phone", columnNames = ["phone_normalized"]),
    UniqueConstraint(name = "uk_client_name_birth_date", columnNames = ["full_name_normalized", "birth_date"])
  ]
)
class Client(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @Column(name = "full_name", nullable = false, length = 150) var fullName: String = "",
  @Column(name = "full_name_normalized", nullable = false, length = 150) var fullNameNormalized: String = "",
  @Column(name = "alternative_contact_name", length = 150) var alternativeContactName: String? = null,
  @Column(nullable = false) var age: Int = 0,
  @Column(name = "birth_date", nullable = false) var birthDate: LocalDate = LocalDate.now(),
  @Column(nullable = false, length = 25) var phone: String = "",
  @Column(name = "phone_normalized", nullable = false, length = 20) var phoneNormalized: String = "",
  @Column(nullable = false, length = 150) var occupation: String = "",
  @Column(nullable = false, length = 150) var email: String = "",
  @Lob @Column(name = "photo_content", nullable = false, columnDefinition = "LONGBLOB") var photoContent: ByteArray = byteArrayOf(),
  @Column(name = "photo_content_type", nullable = false, length = 20) var photoContentType: String = "",
  @Column(nullable = false, length = 150) var street: String = "",
  @Column(nullable = false, length = 100) var neighborhood: String = "",
  @Column(nullable = false, length = 100) var municipality: String = "",
  @Column(nullable = false, length = 100) var state: String = "",
  @Column(name = "postal_code", nullable = false, length = 10) var postalCode: String = "",
  // Referencia opcional para las futuras sucursales A y B; no existe catálogo de sucursales en esta fase.
  @Column(name = "branch_code", length = 1) var branchCode: String? = null,
  @Column(name = "created_by_user_id", nullable = false) var createdByUserId: Long = 0,
  @Column(name = "updated_by_user_id", nullable = false) var updatedByUserId: Long = 0,
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
  @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)

interface ClientRepository : org.springframework.data.jpa.repository.JpaRepository<Client, Long> {
  fun findByEmail(email: String): Client?
  fun findByPhoneNormalized(phoneNormalized: String): Client?
  fun findByFullNameNormalizedAndBirthDate(fullNameNormalized: String, birthDate: LocalDate): Client?
}
