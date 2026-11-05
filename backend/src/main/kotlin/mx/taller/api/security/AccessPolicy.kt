package mx.taller.api.security

import mx.taller.api.domain.Role
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

/** Punto único para los permisos funcionales compartidos por clientes y vehículos. */
@Component("accessPolicy")
class AccessPolicy {
  fun canManageClients(authentication: Authentication?): Boolean = role(authentication) in CLIENT_OPERATORS
  fun isAdmin(authentication: Authentication?): Boolean = role(authentication) == Role.ADMIN
  fun canRegisterVehicles(authentication: Authentication?): Boolean = role(authentication) in CLIENT_OPERATORS

  private fun role(authentication: Authentication?): Role? = authentication?.authorities
    ?.firstOrNull()?.authority?.removePrefix("ROLE_")?.let { runCatching { Role.valueOf(it) }.getOrNull() }

  private companion object { val CLIENT_OPERATORS = setOf(Role.ADMIN, Role.RECEPCIONISTA) }
}
