package mx.taller.api.config.migration

import org.springframework.boot.CommandLineRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** Ejecuta de forma idempotente el script aditivo de la fase actual después de que JPA actualiza el esquema. */
@Component
class PhaseThreeMigration(private val jdbc: JdbcTemplate) : CommandLineRunner {
  override fun run(vararg args: String) {
    listOf("R__fase3_status_vehiculos.sql", "R__fase5_historial_propietarios.sql").forEach(::executeScript)
    addColumnIfMissing("clients", "status_id", "BIGINT NULL")
    addColumnIfMissing("clients", "motivo_status", "TEXT NULL")
    jdbc.execute("update clients c join status s on s.strvalor = case upper(trim(c.estatus)) when 'SUSPENDIDO' then 'SUSPENDIDO' else 'ACTIVO' end set c.status_id = s.id where c.status_id is null")
    addIndexIfMissing("clients", "idx_clients_workshop", "taller_id")
    addIndexIfMissing("clients", "idx_clients_status", "status_id")
    addIndexIfMissing("clients", "idx_clients_full_name", "full_name")
    addIndexIfMissing("vehiculo", "idx_vehiculo_cliente", "cliente_id")
    addForeignKeyIfMissing("clients", "fk_clients_status", "status_id", "status", "id")
    addForeignKeyIfMissing("vehiculo", "fk_vehiculo_cliente", "cliente_id", "clients", "id")
    addForeignKeyIfMissing("vehiculo", "fk_vehiculo_status", "status_id", "status", "id")
    addForeignKeyIfMissing("vehiculo", "fk_vehiculo_marca", "marca_id", "marca", "id")
    addForeignKeyIfMissing("vehiculo", "fk_vehiculo_modelo", "modelo_id", "modelo", "id")
    addIndexIfMissing("vehiculo_historial_propietario", "idx_historial_vehiculo", "vehiculo_id")
    addIndexIfMissing("vehiculo_historial_propietario", "idx_historial_cliente_anterior", "cliente_anterior_id")
    addIndexIfMissing("vehiculo_historial_propietario", "idx_historial_cliente_nuevo", "cliente_nuevo_id")
    addForeignKeyIfMissing("vehiculo_historial_propietario", "fk_historial_vehiculo", "vehiculo_id", "vehiculo", "id")
    addForeignKeyIfMissing("vehiculo_historial_propietario", "fk_historial_cliente_anterior", "cliente_anterior_id", "clients", "id")
    addForeignKeyIfMissing("vehiculo_historial_propietario", "fk_historial_cliente_nuevo", "cliente_nuevo_id", "clients", "id")
    addForeignKeyIfMissing("vehiculo_historial_propietario", "fk_historial_estatus_anterior", "estatus_relacion_anterior_id", "status", "id")
  }

  private fun executeScript(resource: String) {
    val sql = ClassPathResource("db/migration/$resource").inputStream.bufferedReader().use { it.readText() }
    sql.lineSequence().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
      .split(";").map(String::trim).filter(String::isNotBlank).forEach { jdbc.execute(it) }
  }

  private fun addForeignKeyIfMissing(table: String, name: String, column: String, targetTable: String, targetColumn: String) {
    val count = jdbc.queryForObject(
      "select count(*) from information_schema.table_constraints where constraint_schema = database() and table_name = ? and constraint_name = ?",
      Long::class.java, table, name
    ) ?: 0
    if (count == 0L) jdbc.execute("alter table `$table` add constraint `$name` foreign key (`$column`) references `$targetTable` (`$targetColumn`)")
  }

  private fun addColumnIfMissing(table: String, column: String, definition: String) {
    val count = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?", Long::class.java, table, column) ?: 0
    if (count == 0L) jdbc.execute("alter table `$table` add column `$column` $definition")
  }

  private fun addIndexIfMissing(table: String, name: String, columns: String) {
    val count = jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema = database() and table_name = ? and index_name = ?", Long::class.java, table, name) ?: 0
    if (count == 0L) jdbc.execute("create index `$name` on `$table` ($columns)")
  }
}
