-- Migración aditiva e idempotente para conservar transferencias de vehículos.
CREATE TABLE IF NOT EXISTS vehiculo_historial_propietario (
  id BIGINT NOT NULL AUTO_INCREMENT,
  vehiculo_id BIGINT NOT NULL,
  cliente_anterior_id BIGINT NOT NULL,
  cliente_nuevo_id BIGINT NOT NULL,
  fecha_cambio DATETIME(6) NOT NULL,
  usuario_id BIGINT NOT NULL,
  estatus_relacion_anterior_id BIGINT NOT NULL,
  PRIMARY KEY (id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
