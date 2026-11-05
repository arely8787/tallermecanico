package mx.taller.api.postal

import jakarta.persistence.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.io.BufferedReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.util.Locale

@Entity
@Table(
  name = "postal_codes",
  uniqueConstraints = [UniqueConstraint(name = "uk_postal_code_location", columnNames = ["postal_code", "state_normalized", "municipality_normalized", "neighborhood_normalized"])],
  indexes = [
    Index(name = "idx_postal_code", columnList = "postal_code"),
    Index(name = "idx_postal_state", columnList = "state_normalized"),
    Index(name = "idx_postal_state_municipality", columnList = "state_normalized, municipality_normalized"),
    Index(name = "idx_postal_neighborhood", columnList = "neighborhood_normalized")
  ]
)
class PostalCodeEntry(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @Column(name = "postal_code", nullable = false, length = 5) var postalCode: String = "",
  @Column(nullable = false, length = 120) var neighborhood: String = "",
  @Column(name = "neighborhood_normalized", nullable = false, length = 120) var neighborhoodNormalized: String = "",
  @Column(nullable = false, length = 120) var municipality: String = "",
  @Column(name = "municipality_normalized", nullable = false, length = 120) var municipalityNormalized: String = "",
  @Column(nullable = false, length = 120) var state: String = "",
  @Column(name = "state_normalized", nullable = false, length = 120) var stateNormalized: String = ""
)

interface PostalCodeRepository : org.springframework.data.jpa.repository.JpaRepository<PostalCodeEntry, Long> {
  fun findAllByPostalCodeOrderByStateAscMunicipalityAscNeighborhoodAsc(postalCode: String): List<PostalCodeEntry>
  fun existsByPostalCodeAndStateNormalizedAndMunicipalityNormalizedAndNeighborhoodNormalized(postalCode: String, state: String, municipality: String, neighborhood: String): Boolean
  @org.springframework.data.jpa.repository.Query("select distinct p.state from PostalCodeEntry p order by p.state")
  fun findStates(): List<String>
  @org.springframework.data.jpa.repository.Query("select distinct p.municipality from PostalCodeEntry p where p.stateNormalized = :state order by p.municipality")
  fun findMunicipalities(@org.springframework.data.repository.query.Param("state") state: String): List<String>
  fun findAllByStateNormalizedAndMunicipalityNormalizedOrderByNeighborhoodAscPostalCodeAsc(state: String, municipality: String): List<PostalCodeEntry>
  @org.springframework.data.jpa.repository.Query("select p from PostalCodeEntry p where p.neighborhoodNormalized like concat('%', :query, '%') and (:state is null or p.stateNormalized = :state) and (:municipality is null or p.municipalityNormalized = :municipality) order by p.neighborhood, p.municipality, p.state, p.postalCode")
  fun searchNeighborhoods(@org.springframework.data.repository.query.Param("query") query: String, @org.springframework.data.repository.query.Param("state") state: String?, @org.springframework.data.repository.query.Param("municipality") municipality: String?, pageable: org.springframework.data.domain.Pageable): List<PostalCodeEntry>
}

data class MunicipalityPostalCodeView(val municipality: String, val neighborhoods: List<String>)
data class PostalCodeView(val state: String, val municipalities: List<MunicipalityPostalCodeView>)
data class NeighborhoodView(val neighborhood: String, val postalCodes: List<String>)
data class NeighborhoodSearchView(val neighborhood: String, val municipality: String, val state: String, val postalCodes: List<String>)

@Service
class PostalCodeFacade(private val postalCodes: PostalCodeRepository) {
  fun find(postalCode: String): PostalCodeView {
    val entries = postalCodes.findAllByPostalCodeOrderByStateAscMunicipalityAscNeighborhoodAsc(validatePostalCode(postalCode))
    if (entries.isEmpty()) notFound(postalCode)
    val state = entries.first().state
    return PostalCodeView(
      state = state,
      municipalities = entries
        .groupBy { it.municipality }
        .map { (municipality, locations) -> MunicipalityPostalCodeView(municipality, locations.map { it.neighborhood }) }
    )
  }

  fun validateAddress(postalCode: String, state: String, municipality: String, neighborhood: String) {
    val normalizedPostalCode = validatePostalCode(postalCode)
    if (postalCodes.findAllByPostalCodeOrderByStateAscMunicipalityAscNeighborhoodAsc(normalizedPostalCode).isEmpty()) notFound(normalizedPostalCode)
    if (!postalCodes.existsByPostalCodeAndStateNormalizedAndMunicipalityNormalizedAndNeighborhoodNormalized(
        normalizedPostalCode, normalizePostalText(state), normalizePostalText(municipality), normalizePostalText(neighborhood)
      )) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado, municipio o colonia no corresponde al código postal indicado")
    }
  }

  fun states(): List<String> = postalCodes.findStates()

  fun municipalities(state: String): List<String> = postalCodes.findMunicipalities(requiredText(state, "El estado es obligatorio"))

  fun neighborhoods(state: String, municipality: String): List<NeighborhoodView> = postalCodes
    .findAllByStateNormalizedAndMunicipalityNormalizedOrderByNeighborhoodAscPostalCodeAsc(
      requiredText(state, "El estado es obligatorio"), requiredText(municipality, "El municipio es obligatorio")
    )
    .groupBy { it.neighborhood }
    .map { (name, entries) -> NeighborhoodView(name, entries.map { it.postalCode }.distinct()) }

  fun searchNeighborhoods(query: String, state: String?, municipality: String?): List<NeighborhoodSearchView> {
    val normalizedQuery = normalizePostalText(query)
    if (normalizedQuery.length < 3) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos 3 letras para buscar una colonia")
    val normalizedState = state?.takeIf { it.isNotBlank() }?.let(::normalizePostalText)
    val normalizedMunicipality = municipality?.takeIf { it.isNotBlank() }?.let(::normalizePostalText)
    return postalCodes.searchNeighborhoods(normalizedQuery, normalizedState, normalizedMunicipality, org.springframework.data.domain.PageRequest.of(0, 20))
      .groupBy { listOf(it.neighborhood, it.municipality, it.state) }
      .map { (key, entries) -> NeighborhoodSearchView(key[0], key[1], key[2], entries.map { it.postalCode }.distinct()) }
  }

  private fun validatePostalCode(postalCode: String): String {
    val value = postalCode.trim()
    if (!POSTAL_CODE.matches(value)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El código postal debe contener 5 dígitos")
    return value
  }

  private fun notFound(postalCode: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "No existe información para el código postal $postalCode")
  private fun requiredText(value: String, message: String): String = value.trim().ifBlank { throw ResponseStatusException(HttpStatus.BAD_REQUEST, message) }.let(::normalizePostalText)

  private companion object { val POSTAL_CODE = Regex("\\d{5}") }
}

@RestController
@RequestMapping("/api/codigos-postales")
class PostalCodeController(private val facade: PostalCodeFacade) {
  @GetMapping("/estados") fun states(): List<String> = facade.states()
  @GetMapping("/municipios") fun municipalities(@RequestParam estado: String): List<String> = facade.municipalities(estado)
  @GetMapping("/colonias") fun neighborhoods(@RequestParam estado: String, @RequestParam municipio: String): List<NeighborhoodView> = facade.neighborhoods(estado, municipio)
  @GetMapping("/buscar-colonias") fun searchNeighborhoods(@RequestParam q: String, @RequestParam(required = false) estado: String?, @RequestParam(required = false) municipio: String?): List<NeighborhoodSearchView> = facade.searchNeighborhoods(q, estado, municipio)
  @GetMapping("/{postalCode}")
  fun find(@PathVariable postalCode: String): PostalCodeView = facade.find(postalCode)
}

@Component
class PostalCodeCatalogLoader(
  private val postalCodes: PostalCodeRepository,
  private val jdbcTemplate: JdbcTemplate,
  private val transactionTemplate: TransactionTemplate,
  @Value("\${postal.catalog-path:data/CPdescarga.txt}") private val catalogPath: String
) : CommandLineRunner {
  override fun run(vararg args: String) {
    if (postalCodes.count() != 0L) return
    transactionTemplate.executeWithoutResult { loadCatalog() }
  }

  private fun loadCatalog() {
    val catalog = Path.of(catalogPath)
    if (!Files.isRegularFile(catalog)) throw IllegalStateException("No se encontró el catálogo SEPOMEX en $catalogPath")
    var loaded = 0
    Files.newBufferedReader(catalog, Charset.forName("ISO-8859-1")).use { reader ->
      val headers = findHeaders(reader)
      val postalCodeIndex = headers.getValue("d_codigo")
      val neighborhoodIndex = headers.getValue("d_asenta")
      val municipalityIndex = headers.getValue("D_mnpio")
      val stateIndex = headers.getValue("d_estado")
      val rows = mutableListOf<Array<Any>>()
      reader.forEachLine { rawLine ->
        val fields = rawLine.trimEnd('\r').split('|')
        val postalCode = fields.valueAt(postalCodeIndex).trim()
        val neighborhood = fields.valueAt(neighborhoodIndex).trim()
        val municipality = fields.valueAt(municipalityIndex).trim()
        val state = fields.valueAt(stateIndex).trim()
        if (POSTAL_CODE.matches(postalCode) && neighborhood.isNotBlank() && municipality.isNotBlank() && state.isNotBlank()) {
          rows += arrayOf(postalCode, neighborhood, normalizePostalText(neighborhood), municipality, normalizePostalText(municipality), state, normalizePostalText(state))
          if (rows.size == BATCH_SIZE) {
            loaded += insertBatch(rows)
            rows.clear()
          }
        }
      }
      if (rows.isNotEmpty()) loaded += insertBatch(rows)
    }
    println("Catálogo SEPOMEX cargado: $loaded registros.")
  }

  private fun findHeaders(reader: BufferedReader): Map<String, Int> {
    while (true) {
      val line = reader.readLine()?.trimEnd('\r') ?: break
      val fields = line.split('|')
      if (fields.firstOrNull() == "d_codigo") return fields.withIndex().associate { it.value to it.index }
    }
    throw IllegalStateException("El catálogo SEPOMEX no contiene la fila d_codigo")
  }

  private fun insertBatch(rows: List<Array<Any>>): Int =
    jdbcTemplate.batchUpdate(
      "insert ignore into postal_codes (postal_code, neighborhood, neighborhood_normalized, municipality, municipality_normalized, state, state_normalized) values (?, ?, ?, ?, ?, ?, ?)",
      rows
    ).size

  private fun List<String>.valueAt(index: Int): String = getOrNull(index) ?: ""

  private companion object {
    const val BATCH_SIZE = 1_000
    val POSTAL_CODE = Regex("\\d{5}")
  }
}

fun normalizePostalText(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
  .replace(Regex("\\p{M}+"), "")
  .lowercase(Locale.ROOT)
  .replace(Regex("\\s+"), " ")
