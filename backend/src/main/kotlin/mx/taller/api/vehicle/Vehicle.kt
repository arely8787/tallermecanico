package mx.taller.api.vehicle

import jakarta.persistence.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import mx.taller.api.client.Client
import mx.taller.api.client.ClientRepository
import mx.taller.api.status.Status
import mx.taller.api.status.StatusFacade
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.security.core.Authentication
import java.time.Instant
import java.time.Year
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Entity @Table(name = "marca", uniqueConstraints = [UniqueConstraint(name = "uk_marca_nombre", columnNames = ["nombre_normalizado"])])
class VehicleBrand(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @Column(nullable = false, length = 100) var nombre: String = "",
  @Column(name = "nombre_normalizado", nullable = false, length = 100) var normalizedName: String = ""
)

@Entity @Table(name = "modelo", uniqueConstraints = [UniqueConstraint(name = "uk_modelo_marca_nombre", columnNames = ["marca_id", "nombre_normalizado"])])
class VehicleModel(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "marca_id", nullable = false) var brand: VehicleBrand? = null,
  @Column(nullable = false, length = 120) var nombre: String = "",
  @Column(name = "nombre_normalizado", nullable = false, length = 120) var normalizedName: String = ""
)

@Entity @Table(name = "vehiculo", uniqueConstraints = [
  UniqueConstraint(name = "uk_vehiculo_vin", columnNames = ["vin"]),
  UniqueConstraint(name = "uk_vehiculo_serie", columnNames = ["numero_serie"]),
  UniqueConstraint(name = "uk_vehiculo_placa", columnNames = ["placa"])
], indexes = [Index(name = "idx_vehiculo_cliente", columnList = "cliente_id")])
class Vehicle(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @Column(nullable = false, length = 17) var vin: String = "",
  @Column(name = "numero_serie", nullable = false, length = 20) var serialNumber: String = "",
  @Column(nullable = false, length = 8) var placa: String = "",
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "marca_id", nullable = false) var brand: VehicleBrand? = null,
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "modelo_id", nullable = false) var model: VehicleModel? = null,
  @Column(nullable = false) var anio: Int = 0,
  @Column(nullable = false, length = 100) var version: String = "",
  @Column(nullable = false, length = 60) var color: String = "",
  @Column var kilometraje: Int? = null,
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cliente_id", nullable = false) var client: Client? = null,
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "status_id", nullable = false) var status: Status? = null
)

/** Registro inmutable de una transferencia; el vehículo conserva una única propiedad vigente. */
@Entity
@Table(name = "vehiculo_historial_propietario", indexes = [
  Index(name = "idx_historial_vehiculo", columnList = "vehiculo_id"),
  Index(name = "idx_historial_cliente_anterior", columnList = "cliente_anterior_id"),
  Index(name = "idx_historial_cliente_nuevo", columnList = "cliente_nuevo_id")
])
class VehicleOwnershipHistory(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vehiculo_id", nullable = false) var vehicle: Vehicle? = null,
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cliente_anterior_id", nullable = false) var previousClient: Client? = null,
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cliente_nuevo_id", nullable = false) var newClient: Client? = null,
  @Column(name = "fecha_cambio", nullable = false) var changedAt: Instant = Instant.now(),
  @Column(name = "usuario_id", nullable = false) var changedByUserId: Long = 0,
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "estatus_relacion_anterior_id", nullable = false) var previousRelationshipStatus: Status? = null
)

interface VehicleBrandRepository : org.springframework.data.jpa.repository.JpaRepository<VehicleBrand, Long> {
  fun findByNormalizedName(normalizedName: String): VehicleBrand?
  fun findTop50ByOrderByNombreAsc(): List<VehicleBrand>
  fun findTop50ByNormalizedNameContainingOrderByNombreAsc(normalizedName: String): List<VehicleBrand>
}
interface VehicleModelRepository : org.springframework.data.jpa.repository.JpaRepository<VehicleModel, Long> {
  fun findByBrandIdAndNormalizedName(brandId: Long, normalizedName: String): VehicleModel?
  fun findAllByBrandIdOrderByNombreAsc(brandId: Long): List<VehicleModel>
  fun findTop20ByBrandIdAndNormalizedNameContainingOrderByNombreAsc(brandId: Long, normalizedName: String): List<VehicleModel>
  fun findTop20ByOrderByNombreAsc(): List<VehicleModel>
  fun findTop20ByNormalizedNameContainingOrderByNombreAsc(normalizedName: String): List<VehicleModel>
}
interface VehicleRepository : org.springframework.data.jpa.repository.JpaRepository<Vehicle, Long> {
  fun existsByVin(vin: String): Boolean
  fun existsBySerialNumber(serialNumber: String): Boolean
  fun existsByPlaca(placa: String): Boolean
  fun existsByVinAndIdNot(vin: String, id: Long): Boolean
  fun existsBySerialNumberAndIdNot(serialNumber: String, id: Long): Boolean
  fun existsByPlacaAndIdNot(placa: String, id: Long): Boolean
  @org.springframework.data.jpa.repository.Query("""
    select case when count(v) > 0 then true else false end from Vehicle v join v.status s
    where v.id <> :vehicleId and s.value <> 'ELIMINADO' and (v.vin = :vin or v.placa = :placa)
  """)
  fun existsActiveOwnershipConflict(@org.springframework.data.repository.query.Param("vehicleId") vehicleId: Long, @org.springframework.data.repository.query.Param("vin") vin: String, @org.springframework.data.repository.query.Param("placa") placa: String): Boolean
  @org.springframework.data.jpa.repository.Query("select distinct v.anio from Vehicle v where v.model.id = :modelId order by v.anio desc")
  fun findYearsByModelId(@org.springframework.data.repository.query.Param("modelId") modelId: Long): List<Int>
  @org.springframework.data.jpa.repository.Query("select distinct v.version from Vehicle v where v.model.id = :modelId and (:query = '' or lower(v.version) like concat('%', :query, '%')) order by v.version")
  fun findVersionsByModelId(@org.springframework.data.repository.query.Param("modelId") modelId: Long, @org.springframework.data.repository.query.Param("query") query: String): List<String>
  @org.springframework.data.jpa.repository.Query("""
    select new mx.taller.api.vehicle.VehicleListView(v.id, c.fullName, c.workshopId, w.name, b.nombre, m.nombre, v.anio, v.placa, v.vin, s.value)
    from Vehicle v join v.client c join v.brand b join v.model m join v.status s
    left join mx.taller.api.workshop.Workshop w on w.id = c.workshopId
    where s.value <> 'ELIMINADO' and (:workshopId is null or c.workshopId = :workshopId)
      and (:query = '' or lower(v.placa) like concat('%', :query, '%') or lower(v.vin) like concat('%', :query, '%') or lower(c.fullName) like concat('%', :query, '%'))
    order by c.fullName asc, v.id desc
  """)
  fun findForAdministration(@org.springframework.data.repository.query.Param("query") query: String, @org.springframework.data.repository.query.Param("workshopId") workshopId: Long?): List<VehicleListView>
  @org.springframework.data.jpa.repository.Query("""
    select new mx.taller.api.vehicle.VehicleListView(v.id, c.fullName, c.workshopId, w.name, b.nombre, m.nombre, v.anio, v.placa, v.vin, s.value)
    from Vehicle v join v.client c join v.brand b join v.model m join v.status s
    left join mx.taller.api.workshop.Workshop w on w.id = c.workshopId
    where c.id = :clientId and s.value <> 'ELIMINADO'
    order by v.id desc
  """)
  fun findCurrentByClientId(@org.springframework.data.repository.query.Param("clientId") clientId: Long): List<VehicleListView>
  /** Recupera únicamente la propiedad vigente para la consulta exacta por VIN o placa. */
  @org.springframework.data.jpa.repository.Query("""
    select new mx.taller.api.vehicle.VehicleLookupView(v.id, v.vin, v.serialNumber, v.placa, b.nombre, m.nombre, v.anio, v.version, v.color, v.kilometraje, c.id, c.fullName, w.id, w.name, s.value)
    from Vehicle v join v.client c join v.brand b join v.model m join v.status s
    left join mx.taller.api.workshop.Workshop w on w.id = c.workshopId
    where s.value <> 'ELIMINADO' and ((:vin is not null and v.vin = :vin) or (:placa is not null and v.placa = :placa))
  """)
  fun findCurrentForLookup(@org.springframework.data.repository.query.Param("vin") vin: String?, @org.springframework.data.repository.query.Param("placa") placa: String?): VehicleLookupView?
}

interface VehicleOwnershipHistoryRepository : org.springframework.data.jpa.repository.JpaRepository<VehicleOwnershipHistory, Long>

data class VehicleCatalogItem(val id: Long, val nombre: String)
data class VehicleModelOption(val id: Long, val nombre: String, val marcaId: Long, val marca: String, val anios: List<Int>)
data class VehicleListView(val id: Long, val cliente: String, val tallerId: Long?, val taller: String?, val marca: String, val modelo: String, val anio: Int, val placa: String, val vin: String, val status: String)
data class VehicleDetailView(val id: Long, val vin: String, val numeroSerie: String, val placa: String, val marcaId: Long, val marca: String, val modeloId: Long, val modelo: String, val anio: Int, val version: String, val color: String, val kilometraje: Int?, val clienteId: Long, val cliente: String, val status: String)
/** Proyección de solo lectura del vehículo, su propietario vigente y el taller asociado a ese cliente. */
data class VehicleLookupView(val id: Long, val vin: String, val numeroSerie: String, val placa: String, val marca: String, val modelo: String, val anio: Int, val version: String, val color: String, val kilometraje: Int?, val clienteId: Long, val cliente: String, val tallerId: Long?, val taller: String?, val status: String)

/** Resultado estable de la búsqueda; [vehicle] es nulo cuando no existe una coincidencia vigente. */
data class VehicleLookupResponse(val found: Boolean, val vehicle: VehicleLookupView? = null)
data class VinDecodeView(val anio: Int? = null, val marca: String? = null, val modelo: String? = null, val version: String? = null)
data class VehicleRequest(
  @field:NotBlank val vin: String, @field:NotBlank val numeroSerie: String, @field:NotBlank val placa: String,
  @field:NotBlank val marca: String, @field:NotBlank val modelo: String, @field:Min(1980) val anio: Int,
  @field:NotBlank @field:Size(max = 100) val version: String, @field:NotBlank @field:Size(max = 60) val color: String,
  @field:Min(0) val kilometraje: Int?, @field:NotNull val clienteId: Long
)
data class VehicleView(val id: Long, val vin: String, val numeroSerie: String, val placa: String, val marca: String, val modelo: String, val anio: Int, val version: String, val color: String, val kilometraje: Int?, val clienteId: Long, val status: String)
data class VehicleTransferRequest(@field:NotNull val clienteDestinoId: Long)
data class VehicleTransferView(val id: Long, val clienteId: Long, val cliente: String, val transferred: Boolean)

@Service
class VehicleCatalogFacade(private val brands: VehicleBrandRepository, private val models: VehicleModelRepository, private val vehicles: VehicleRepository) {
  private val requestFactory = SimpleClientHttpRequestFactory().apply { setConnectTimeout(1_000); setReadTimeout(2_000) }
  private val nhtsa = RestClient.builder().requestFactory(requestFactory).baseUrl("https://vpic.nhtsa.dot.gov/api/vehicles").build()
  private val brandCatalogLoaded = AtomicBoolean(false)
  private val modelCatalogLoaded = ConcurrentHashMap.newKeySet<String>()

  @Transactional
  fun brands(query: String?, refresh: Boolean = false): List<VehicleCatalogItem> {
    val term = normalize(query.orEmpty())
    if (term.isNotEmpty() && term.length < 3) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos 3 letras para buscar una marca")
    if (refresh || term.length >= 3) runCatching { cacheBrands() }
    val results = if (term.isBlank()) brands.findTop50ByOrderByNombreAsc() else brands.findTop50ByNormalizedNameContainingOrderByNombreAsc(term)
    return results.map { VehicleCatalogItem(it.id!!, it.nombre) }
  }

  @Transactional
  fun models(brandId: Long, year: Int, query: String?): List<VehicleModelOption> {
    if (!brands.existsById(brandId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "La marca seleccionada no existe")
    validateYear(year)
    val term = normalize(query.orEmpty())
    if (term.isNotEmpty() && term.length < 3) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos 3 letras para buscar un modelo")
    val brand = brands.findById(brandId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "La marca seleccionada no existe") }
    runCatching { cacheModels(brand, year) }
    val results = if (term.isBlank()) models.findAllByBrandIdOrderByNombreAsc(brandId) else models.findTop20ByBrandIdAndNormalizedNameContainingOrderByNombreAsc(brandId, term)
    return results.map(::modelOption)
  }

  @Transactional(readOnly = true)
  fun cachedModels(query: String?): List<VehicleModelOption> {
    val term = normalize(query.orEmpty())
    if (term.isNotEmpty() && term.length < 3) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos 3 letras para buscar un modelo")
    val results = if (term.isBlank()) models.findTop20ByOrderByNombreAsc() else models.findTop20ByNormalizedNameContainingOrderByNombreAsc(term)
    return results.map(::modelOption)
  }

  @Transactional(readOnly = true)
  fun versions(modelId: Long, query: String?): List<String> = vehicles.findVersionsByModelId(modelId, normalize(query.orEmpty())).take(20)

  fun decodeVin(vin: String): VinDecodeView {
    if (!VIN.matches(vin.trim().uppercase(Locale.ROOT))) return VinDecodeView()
    return runCatching {
      val response = nhtsa.get().uri("/DecodeVin/{vin}?format=json", vin.trim().uppercase(Locale.ROOT)).retrieve().body(Map::class.java)
      @Suppress("UNCHECKED_CAST") val values = (response?.get("Results") as? List<Map<String, Any?>>).orEmpty().associate { row -> row["Variable"]?.toString().orEmpty() to row["Value"]?.toString()?.trim().orEmpty() }
      VinDecodeView(values["Model Year"]?.toIntOrNull(), values["Make"].orEmpty().ifBlank { null }, values["Model"].orEmpty().ifBlank { null }, values["Trim"].orEmpty().ifBlank { null })
    }.getOrDefault(VinDecodeView())
  }

  @Transactional
  fun resolveBrand(name: String): VehicleBrand {
    val clean = required(name, "La marca es obligatoria")
    val normalized = normalize(clean)
    return brands.findByNormalizedName(normalized) ?: brands.save(VehicleBrand(nombre = clean, normalizedName = normalized))
  }

  @Transactional
  fun resolveModel(brand: VehicleBrand, name: String): VehicleModel {
    val clean = required(name, "El modelo es obligatorio")
    val normalized = normalize(clean)
    return models.findByBrandIdAndNormalizedName(brand.id!!, normalized) ?: models.save(VehicleModel(brand = brand, nombre = clean, normalizedName = normalized))
  }

  private fun fetchBrands() {
    val all = mutableSetOf<String>()
    listOf("car", "truck").forEach { type ->
      val response = nhtsa.get().uri("/GetMakesForVehicleType/$type?format=json").retrieve().body(Map::class.java) ?: return@forEach
      @Suppress("UNCHECKED_CAST") (response["Results"] as? List<Map<String, Any?>>).orEmpty().forEach { row -> row["MakeName"]?.toString()?.takeIf(String::isNotBlank)?.let(all::add) }
    }
    val existing = brands.findAll().map { it.normalizedName }.toSet()
    all.map { it.trim() }.filter { normalize(it) !in existing }.map { VehicleBrand(nombre = it, normalizedName = normalize(it)) }
      .chunked(50).forEach { brands.saveAll(it) }
  }

  private fun fetchModels(brand: VehicleBrand, year: Int) {
    val response = nhtsa.get().uri("/GetModelsForMakeYear/make/{make}/modelyear/{year}?format=json", brand.nombre, year).retrieve().body(Map::class.java) ?: return
    @Suppress("UNCHECKED_CAST") (response["Results"] as? List<Map<String, Any?>>).orEmpty()
      .forEach { row -> row["Model_Name"]?.toString()?.takeIf(String::isNotBlank)?.let { resolveModel(brand, it) } }
  }

  private fun cacheBrands() {
    if (brandCatalogLoaded.get()) return
    synchronized(brandCatalogLoaded) {
      if (!brandCatalogLoaded.get()) {
        fetchBrands()
        brandCatalogLoaded.set(true)
      }
    }
  }

  private fun cacheModels(brand: VehicleBrand, year: Int) {
    val key = "${brand.id}:$year"
    if (!modelCatalogLoaded.add(key)) return
    try { fetchModels(brand, year) } catch (exception: Exception) { modelCatalogLoaded.remove(key); throw exception }
  }

  private fun modelOption(model: VehicleModel): VehicleModelOption {
    val brand = requireNotNull(model.brand)
    return VehicleModelOption(model.id!!, model.nombre, brand.id!!, brand.nombre, vehicles.findYearsByModelId(model.id!!))
  }

  private fun validateYear(year: Int) { if (year !in 1980..(Year.now().value + 1)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El año debe estar entre 1980 y ${Year.now().value + 1}") }
  private fun required(value: String, message: String) = value.trim().ifBlank { throw ResponseStatusException(HttpStatus.BAD_REQUEST, message) }
  private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
  private companion object { val VIN = Regex("^[A-HJ-NPR-Z0-9]{17}$") }
}

@Service
class VehicleFacade(
  private val vehicles: VehicleRepository,
  private val clients: ClientRepository,
  private val statuses: StatusFacade,
  private val catalog: VehicleCatalogFacade,
  private val ownershipHistory: VehicleOwnershipHistoryRepository
) {
  @Transactional(readOnly = true)
  fun list(query: String?, workshopId: Long?): List<VehicleListView> = vehicles.findForAdministration(query.orEmpty().trim().lowercase(Locale.ROOT), workshopId)

  @Transactional(readOnly = true)
  fun get(id: Long): VehicleDetailView = vehicles.findById(id).orElseThrow { notFound() }.detailView()

  /**
   * Consulta un vehículo vigente por un único identificador normalizado.
   * La ausencia es un resultado normal para que la interfaz pueda comunicarla sin tratarla como error.
   * Exige exactamente un criterio (VIN o placa); si llegan ambos o ninguno responde 400.
   * La placa se normaliza sin espacios ni guiones y en mayúsculas.
   */
  @Transactional(readOnly = true)
  fun lookup(vin: String?, placa: String?): VehicleLookupResponse {
    val normalizedVin = vin?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)
    val normalizedPlate = placa?.trim()?.takeIf { it.isNotEmpty() }
      ?.uppercase(Locale.ROOT)?.replace(Regex("[ -]"), "")
    if ((normalizedVin == null) == (normalizedPlate == null)) {
      invalid("Indica solo un VIN o una placa para consultar")
    }
    val vehicle = vehicles.findCurrentForLookup(normalizedVin, normalizedPlate)
    return if (vehicle == null) VehicleLookupResponse(found = false) else VehicleLookupResponse(found = true, vehicle = vehicle)
  }

  @Transactional(readOnly = true)
  fun listCurrentByClient(clientId: Long): List<VehicleListView> {
    if (!clients.existsById(clientId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado")
    return vehicles.findCurrentByClientId(clientId)
  }

  @Transactional
  fun create(request: VehicleRequest): VehicleView {
    val normalized = validate(request)
    if (vehicles.existsByVin(normalized.vin) || vehicles.existsBySerialNumber(normalized.serial) || vehicles.existsByPlaca(normalized.plate)) duplicate()
    val client = clients.findById(request.clienteId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado") }
    if (client.statusRecord?.value != "ACTIVO") throw ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede registrar un vehículo para un cliente ACTIVO")
    val brand = catalog.resolveBrand(request.marca)
    val model = catalog.resolveModel(brand, request.modelo)
    return try {
      vehicles.saveAndFlush(Vehicle(vin = normalized.vin, serialNumber = normalized.serial, placa = normalized.plate, brand = brand, model = model, anio = request.anio, version = normalized.version, color = normalized.color, kilometraje = request.kilometraje, client = client, status = statuses.required("ACTIVO"))).view()
    } catch (_: DataIntegrityViolationException) { duplicate() }
  }

  @Transactional
  fun update(id: Long, request: VehicleRequest): VehicleView {
    val vehicle = vehicles.findById(id).orElseThrow { notFound() }
    val client = requireNotNull(vehicle.client)
    if (request.clienteId != client.id) invalid("No se puede cambiar el cliente de un vehículo")
    if (client.statusRecord?.value != "ACTIVO") throw ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede registrar un vehículo para un cliente ACTIVO")
    val normalized = validate(request)
    if (vehicles.existsByVinAndIdNot(normalized.vin, id) || vehicles.existsBySerialNumberAndIdNot(normalized.serial, id) || vehicles.existsByPlacaAndIdNot(normalized.plate, id)) duplicate()
    val brand = catalog.resolveBrand(request.marca)
    val model = catalog.resolveModel(brand, request.modelo)
    return try {
      vehicle.apply {
        vin = normalized.vin; serialNumber = normalized.serial; placa = normalized.plate; this.brand = brand; this.model = model
        anio = request.anio; version = normalized.version; color = normalized.color; kilometraje = request.kilometraje
      }
      vehicles.saveAndFlush(vehicle).view()
    } catch (_: DataIntegrityViolationException) { duplicate() }
  }

  @Transactional
  fun softDelete(id: Long) {
    val vehicle = vehicles.findById(id).orElseThrow { notFound() }
    if (vehicle.status?.value == "ELIMINADO") return
    vehicle.status = statuses.required("ELIMINADO")
    vehicles.saveAndFlush(vehicle)
  }

  @Transactional
  fun transfer(id: Long, request: VehicleTransferRequest, actorId: Long): VehicleTransferView {
    val vehicle = vehicles.findById(id).orElseThrow { notFound() }
    if (vehicle.status?.value == "ELIMINADO") throw ResponseStatusException(HttpStatus.CONFLICT, "No se puede transferir un vehículo eliminado")
    val previousClient = requireNotNull(vehicle.client)
    val destination = clients.findById(request.clienteDestinoId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente destino no encontrado") }
    if (destination.statusRecord?.value != "ACTIVO") throw ResponseStatusException(HttpStatus.CONFLICT, "El cliente destino debe estar ACTIVO")
    if (previousClient.id == destination.id) return VehicleTransferView(vehicle.id!!, destination.id!!, destination.fullName, false)
    if (vehicles.existsActiveOwnershipConflict(vehicle.id!!, vehicle.vin, vehicle.placa)) {
      throw ResponseStatusException(HttpStatus.CONFLICT, "Existe otro vehículo activo con el mismo VIN o placa; no se completó la transferencia")
    }
    ownershipHistory.save(VehicleOwnershipHistory(
      vehicle = vehicle,
      previousClient = previousClient,
      newClient = destination,
      changedAt = Instant.now(),
      changedByUserId = actorId,
      previousRelationshipStatus = statuses.required("ELIMINADO")
    ))
    vehicle.client = destination
    vehicles.saveAndFlush(vehicle)
    return VehicleTransferView(vehicle.id!!, destination.id!!, destination.fullName, true)
  }

  private fun validate(request: VehicleRequest): NormalizedVehicle {
    val vin = request.vin.trim().uppercase(Locale.ROOT)
    val serial = request.numeroSerie.trim().uppercase(Locale.ROOT)
    val plate = request.placa.trim().uppercase(Locale.ROOT).replace(Regex("[ -]"), "")
    if (!VIN.matches(vin)) invalid("El VIN debe tener 17 caracteres válidos")
    if (!SERIAL.matches(serial)) invalid("El número de serie debe contener de 5 a 20 letras o dígitos")
    if (!PLATE.matches(plate)) invalid("La placa debe contener de 5 a 8 caracteres, al menos una letra y un número")
    if (request.anio !in 1980..(Year.now().value + 1)) invalid("El año debe estar entre 1980 y ${Year.now().value + 1}")
    if (request.kilometraje != null && request.kilometraje < 0) invalid("El kilometraje no puede ser negativo")
    return NormalizedVehicle(vin, serial, plate, required(request.version, "La versión es obligatoria"), required(request.color, "El color es obligatorio"))
  }
  private fun Vehicle.view() = VehicleView(id!!, vin, serialNumber, placa, brand!!.nombre, model!!.nombre, anio, version, color, kilometraje, client!!.id!!, status!!.value)
  private fun Vehicle.detailView() = VehicleDetailView(id!!, vin, serialNumber, placa, brand!!.id!!, brand!!.nombre, model!!.id!!, model!!.nombre, anio, version, color, kilometraje, client!!.id!!, client!!.fullName, status!!.value)
  private data class NormalizedVehicle(val vin: String, val serial: String, val plate: String, val version: String, val color: String)
  private fun required(value: String, message: String) = value.trim().ifBlank { invalid(message) }
  private fun invalid(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
  private fun duplicate(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un vehículo con el mismo VIN, número de serie o placa")
  private fun notFound(): ResponseStatusException = ResponseStatusException(HttpStatus.NOT_FOUND, "Vehículo no encontrado")
  private companion object { val VIN = Regex("^[A-HJ-NPR-Z0-9]{17}$"); val SERIAL = Regex("^[A-Z0-9]{5,20}$"); val PLATE = Regex("^(?=.*[A-Z])(?=.*\\d)[A-Z0-9]{5,8}$") }
}

@RestController
@RequestMapping("/api/vehiculos")
@PreAuthorize("@accessPolicy.canRegisterVehicles(authentication)")
class VehicleController(private val facade: VehicleFacade, private val catalog: VehicleCatalogFacade) {
  @GetMapping fun list(@RequestParam(required = false) q: String?, @RequestParam(required = false) tallerId: Long?) = facade.list(q, tallerId)
  /** Consulta de solo lectura; la respuesta conserva HTTP 200 cuando no existe coincidencia. */
  @GetMapping("/consulta") fun lookup(@RequestParam(required = false) vin: String?, @RequestParam(required = false) placa: String?) = facade.lookup(vin, placa)
  @GetMapping("/marcas") fun brands(@RequestParam(required = false) q: String?, @RequestParam(defaultValue = "false") refresh: Boolean) = catalog.brands(q, refresh)
  @GetMapping("/modelos") fun cachedModels(@RequestParam(required = false) q: String?) = catalog.cachedModels(q)
  @GetMapping("/modelos/{modelId}/versiones") fun versions(@PathVariable modelId: Long, @RequestParam(required = false) q: String?) = catalog.versions(modelId, q)
  @GetMapping("/marcas/{brandId}/modelos") fun models(@PathVariable brandId: Long, @RequestParam anio: Int, @RequestParam(required = false) q: String?) = catalog.models(brandId, anio, q)
  @GetMapping("/decodificar-vin/{vin}") fun decodeVin(@PathVariable vin: String) = catalog.decodeVin(vin)
  @GetMapping("/cliente/{clientId}") fun listByClient(@PathVariable clientId: Long) = facade.listCurrentByClient(clientId)
  @GetMapping("/{id}") fun get(@PathVariable id: Long) = facade.get(id)
  @PostMapping fun create(@Valid @RequestBody request: VehicleRequest) = org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(facade.create(request))
  @PutMapping("/{id}") fun update(@PathVariable id: Long, @Valid @RequestBody request: VehicleRequest) = facade.update(id, request)
  @PatchMapping("/{id}/eliminar") fun softDelete(@PathVariable id: Long): org.springframework.http.ResponseEntity<Void> { facade.softDelete(id); return org.springframework.http.ResponseEntity.noContent().build() }
  @PatchMapping("/{id}/transferir") fun transfer(@PathVariable id: Long, @Valid @RequestBody request: VehicleTransferRequest, authentication: Authentication) = facade.transfer(id, request, actorId(authentication))

  private fun actorId(authentication: Authentication): Long = authentication.principal as? Long ?: throw IllegalStateException("Usuario autenticado inválido")
}
