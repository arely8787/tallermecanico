package mx.taller.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TallerApplication
fun main(args: Array<String>) {
  runApplication<TallerApplication>(*args)
}
