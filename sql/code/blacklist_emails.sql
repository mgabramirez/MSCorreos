-- Blacklist Emails Table - Global component (no empresa_id)
-- This table is shared across all services in the ecosystem

CREATE TABLE blacklist_emails (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    razon VARCHAR(500),
    fecha_bloqueo TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bloqueado_por VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT true,
    
    -- Auditoría
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP,
    usuario_creacion VARCHAR(100),
    usuario_actualizacion VARCHAR(100)
);

-- Índice único para búsquedas rápidas de emails activos
-- Solo permite un email activo a la vez
CREATE UNIQUE INDEX idx_blacklist_email_unico 
ON blacklist_emails(email) 
WHERE activo = true;

-- Índice para búsquedas por estado
CREATE INDEX idx_blacklist_email_activo 
ON blacklist_emails(activo);

-- Índice para búsquedas por fecha (ordenamiento)
CREATE INDEX idx_blacklist_fecha_bloqueo 
ON blacklist_emails(fecha_bloqueo);

-- Comentarios para documentación
COMMENT ON TABLE blacklist_emails IS 'Tabla global de blacklist de correos electrónicos. No está asociada a ninguna empresa.';
COMMENT ON COLUMN blacklist_emails.email IS 'Correo electrónico bloqueado';
COMMENT ON COLUMN blacklist_emails.razon IS 'Razón por la cual fue bloqueado el correo';
COMMENT ON COLUMN blacklist_emails.fecha_bloqueo IS 'Fecha cuando se bloqueó el correo';
COMMENT ON COLUMN blacklist_emails.bloqueado_por IS 'Usuario que realizó el bloqueo';
COMMENT ON COLUMN blacklist_emails.activo IS 'Indica si el registro está activo (soft delete)';