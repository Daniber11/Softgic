-- =============================================================================
--  Creación de las dos bases de datos del sistema.
--
--  Por qué existe este script: la imagen de SQL Server arranca con las bases del
--  sistema únicamente. Flyway migra un esquema, pero no crea la base que lo
--  contiene. Sin este paso los dos servicios fallarían al conectar.
--
--  Es idempotente: se puede reejecutar sin efecto sobre bases ya creadas.
-- =============================================================================

IF DB_ID('solicitudes_db') IS NULL
BEGIN
    CREATE DATABASE solicitudes_db;
    PRINT 'Base creada: solicitudes_db';
END
ELSE
    PRINT 'Base ya existente, sin cambios: solicitudes_db';
GO

IF DB_ID('indicadores_db') IS NULL
BEGIN
    CREATE DATABASE indicadores_db;
    PRINT 'Base creada: indicadores_db';
END
ELSE
    PRINT 'Base ya existente, sin cambios: indicadores_db';
GO

-- El nivel READ_COMMITTED_SNAPSHOT evita que las lecturas de la bandeja bloqueen
-- a los escritores. Es relevante para el escenario A2: dos analistas compitiendo
-- no deben quedar serializados por una consulta de listado concurrente.
ALTER DATABASE solicitudes_db SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO

ALTER DATABASE indicadores_db SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO

PRINT 'db-init completado correctamente.';
GO
