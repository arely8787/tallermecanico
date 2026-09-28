package mx.taller.api.config

import mx.taller.api.domain.*
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class SeedData {
 @Bean fun seed(users: UserRepository, encoder: PasswordEncoder) = CommandLineRunner {
   if (users.findByEmail("gerente@taller.local") == null) users.save(User(name="Gerente inicial", email="gerente@taller.local", passwordHash=encoder.encode("Gerente123!"), role=Role.GERENTE, status=UserStatus.ACTIVE))
 }
}
