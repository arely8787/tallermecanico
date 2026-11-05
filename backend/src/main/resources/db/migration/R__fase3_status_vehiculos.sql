-- Migración aditiva e idempotente de catálogo de estatus e índices de fase 3.
CREATE TABLE IF NOT EXISTS status (
  id BIGINT NOT NULL AUTO_INCREMENT,
  strvalor VARCHAR(30) NOT NULL,
  strdescripcion VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_status_strvalor (strvalor)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO status (strvalor, strdescripcion) VALUES
  ('ACTIVO', 'Registro disponible y en operación normal.'),
  ('CANCELADO', 'Registro cancelado, reservado para una lógica futura.'),
  ('SUSPENDIDO', 'Registro temporalmente inactivo, conservado para consulta y reactivación.'),
  ('ELIMINADO', 'Registro eliminado de forma lógica, conservado para auditoría y control de duplicados.')
ON DUPLICATE KEY UPDATE strdescripcion = VALUES(strdescripcion);
