-- Esquema de base de datos compartido
-- Todos los módulos (generador, detector, panel) trabajan sobre estas tablas

CREATE TABLE IF NOT EXISTS clientes (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    dni           VARCHAR(20)  NOT NULL UNIQUE,
    nombre        VARCHAR(100) NOT NULL,
    perfil_riesgo ENUM('BAJO', 'MEDIO', 'ALTO') NOT NULL DEFAULT 'BAJO',
    fecha_alta    DATE         NOT NULL,
    kyc_fecha     DATE,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cuentas (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_cuenta VARCHAR(24)  NOT NULL UNIQUE,
    cliente_id    BIGINT       NOT NULL,
    fecha_apertura DATE        NOT NULL,
    activa        BOOLEAN      NOT NULL DEFAULT TRUE,
    FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

CREATE TABLE IF NOT EXISTS transacciones (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id    VARCHAR(36)    NOT NULL UNIQUE,  -- UUID
    timestamp_op      DATETIME(3)    NOT NULL,
    cuenta_origen     VARCHAR(24),
    cuenta_destino    VARCHAR(24),
    importe           DECIMAL(15,2)  NOT NULL,
    divisa            CHAR(3)        NOT NULL DEFAULT 'EUR',
    tipo_operacion    ENUM(
                        'TRANSFERENCIA',
                        'RETIRO_CAJERO',
                        'DEPOSITO',
                        'PAGO_COMERCIO',
                        'PAGO_ONLINE',
                        'CIERRE_CUENTA'
                      ) NOT NULL,
    ip_origen         VARCHAR(45),   -- IPv4 o IPv6
    dispositivo_id    VARCHAR(100),
    pais_origen       CHAR(2),       -- ISO 3166-1 alpha-2
    pais_destino      CHAR(2),
    sesion_id         VARCHAR(36),
    concepto          VARCHAR(255),
    estado            ENUM('COMPLETADA', 'FALLIDA', 'PENDIENTE') NOT NULL,
    empleado_id       BIGINT         -- solo para operaciones internas
);

CREATE TABLE IF NOT EXISTS eventos_sesion (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    sesion_id     VARCHAR(36)  NOT NULL,
    cliente_id    BIGINT       NOT NULL,
    timestamp_ev  DATETIME(3)  NOT NULL,
    tipo_evento   ENUM(
                    'LOGIN_OK',
                    'LOGIN_FALLIDO',
                    'LOGOUT',
                    'CAMBIO_PASSWORD',
                    'CAMBIO_CONTACTO',
                    'CONSULTA_SALDO'
                  ) NOT NULL,
    ip            VARCHAR(45),
    dispositivo_id VARCHAR(100),
    FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

CREATE TABLE IF NOT EXISTS anomalias_detectadas (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp_det   DATETIME(3)   NOT NULL,
    tipo_anomalia   VARCHAR(100)  NOT NULL,
    severidad       ENUM('BAJA', 'MEDIA', 'ALTA', 'CRITICA') NOT NULL,
    cuenta_id       VARCHAR(24),
    transaccion_id  VARCHAR(36),
    descripcion     TEXT,
    revisada        BOOLEAN       NOT NULL DEFAULT FALSE,
    empleado_rev    BIGINT
);

-- Índices para las consultas más habituales del detector
CREATE INDEX idx_trans_cuenta_origen    ON transacciones(cuenta_origen, timestamp_op);
CREATE INDEX idx_trans_cuenta_destino   ON transacciones(cuenta_destino, timestamp_op);
CREATE INDEX idx_trans_ip               ON transacciones(ip_origen, timestamp_op);
CREATE INDEX idx_trans_sesion           ON transacciones(sesion_id);
CREATE INDEX idx_sesion_cliente         ON eventos_sesion(cliente_id, timestamp_ev);
CREATE INDEX idx_anomalias_revisada     ON anomalias_detectadas(revisada);
