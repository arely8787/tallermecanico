package mx.taller.api.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import mx.taller.api.domain.Role
import mx.taller.api.domain.UserRepository
import mx.taller.api.domain.UserStatus

@Configuration @EnableWebSecurity @EnableMethodSecurity
class SecurityConfig(private val tokenFilter: TokenFilter) {
  @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)
  @Bean fun filterChain(http: HttpSecurity): SecurityFilterChain = http
    .csrf { it.disable() }.cors { }
    .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    .authorizeHttpRequests { it.requestMatchers("/api/auth/**").permitAll().anyRequest().authenticated() }
    .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter::class.java)
    .build()
}

@Component
class TokenService {
  private val tokens = ConcurrentHashMap<String, Pair<Long, Role>>()
  fun create(userId: Long, role: Role): String { val bytes=ByteArray(32); SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also { tokens[it]=userId to role } }
  fun lookup(token: String) = tokens[token]
}

@Component
class TokenFilter(private val tokens: TokenService, private val users: UserRepository): OncePerRequestFilter() {
 override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
   val token=req.getHeader("Authorization")?.removePrefix("Bearer ")
   token?.let { tokens.lookup(it) }?.let { (id, _) ->
     val user = users.findById(id).orElse(null)
     if (user?.status != UserStatus.ACTIVE) return@let
     val auth=UsernamePasswordAuthenticationToken(id, null, listOf(SimpleGrantedAuthority("ROLE_${user.role}")))
     org.springframework.security.core.context.SecurityContextHolder.getContext().authentication=auth
   }
   chain.doFilter(req,res)
 }
}
