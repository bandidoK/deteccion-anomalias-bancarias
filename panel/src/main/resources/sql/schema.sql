-- SQL schema para persistencia de logs de auditoría
-- Incluye una versión genérica y una sección opcional para PostgreSQL.
-- Ajusta tipos y generación de ID según la base de datos que uses.

/*
  Versión genérica (INT/IDENTITY puede variar según el motor):
*/
CREATE TABLE audit_log (
  id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  event_id VARCHAR(100),
  event_timestamp TIMESTAMP NOT NULL,
  source VARCHAR(200),
  level VARCHAR(20),
  message TEXT,
  metadata TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_timestamp ON audit_log (event_timestamp);
CREATE INDEX idx_audit_log_source ON audit_log (source);
CREATE INDEX idx_audit_log_level ON audit_log (level);

/*
  PostgreSQL (recomendado para producción):

  CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_id TEXT,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    source TEXT,
    level TEXT,
    message TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
  );

  -- Índices útiles
  CREATE INDEX idx_audit_log_event_ts ON audit_log (event_timestamp);
  CREATE INDEX idx_audit_log_source ON audit_log (source);
  CREATE INDEX idx_audit_log_level ON audit_log (level);
  -- Índice GIN para búsquedas de texto completo sobre `message` (opcional)
  CREATE INDEX idx_audit_log_message_gin ON audit_log USING GIN (to_tsvector('simple', message));
*/

-- Nota: Si usas MySQL/MariaDB adapta tipos y la sintaxis de generación de ID
-- (por ejemplo, `id BIGINT AUTO_INCREMENT PRIMARY KEY` y `JSON` en lugar de `JSONB`).

-- Tabla para almacenar anomalías detectadas
CREATE TABLE anomalies (
  id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  timestamp_detection TIMESTAMP NOT NULL,
  tipo_anomalia VARCHAR(100) NOT NULL,
  severidad VARCHAR(10) NOT NULL,
  entidad_sospechosa VARCHAR(255),
  descripcion TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_anomalies_timestamp ON anomalies (timestamp_detection);
CREATE INDEX idx_anomalies_tipo ON anomalies (tipo_anomalia);
CREATE INDEX idx_anomalies_entidad ON anomalies (entidad_sospechosa);

-- Tabla para alertas de auditoría. El frontend escucha esta tabla para refrescar el gráfico.
CREATE TABLE IF NOT EXISTS alertas_auditoria (
  id BIGSERIAL PRIMARY KEY,
  timestamp_detection TIMESTAMP WITH TIME ZONE NOT NULL,
  tipo_alerta TEXT NOT NULL,
  severidad TEXT NOT NULL,
  entidad_sospechosa TEXT,
  descripcion TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alertas_auditoria_timestamp ON alertas_auditoria (timestamp_detection);
CREATE INDEX IF NOT EXISTS idx_alertas_auditoria_tipo ON alertas_auditoria (tipo_alerta);
CREATE INDEX IF NOT EXISTS idx_alertas_auditoria_severidad ON alertas_auditoria (severidad);

-- Trigger y función para PostgreSQL que notifican al canal `anomalies_channel`
-- cuando se inserta una nueva fila en `alertas_auditoria`.
CREATE OR REPLACE FUNCTION notify_alertas_auditoria() RETURNS trigger AS $$
BEGIN
  PERFORM pg_notify('anomalies_channel', NEW.id::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS alertas_auditoria_notify_after_insert ON alertas_auditoria;

CREATE TRIGGER alertas_auditoria_notify_after_insert
AFTER INSERT ON alertas_auditoria
FOR EACH ROW
EXECUTE FUNCTION notify_alertas_auditoria();
