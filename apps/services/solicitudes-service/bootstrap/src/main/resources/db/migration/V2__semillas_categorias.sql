-- =============================================================================
--  V2 - Semillas del catalogo de categorias
--
--  DATOS NO SENSIBLES. Son categorias operativas genericas inventadas para la
--  demostracion. No provienen de ningun sistema real ni corresponden a la
--  estructura interna de ninguna entidad.
--
--  Los identificadores son fijos y no aleatorios: asi las pruebas de aceptacion
--  de Karate pueden referenciar una categoria concreta sin consultarla antes.
-- =============================================================================

INSERT INTO categoria (id, codigo, nombre, activa) VALUES
    ('11111111-1111-4111-8111-111111111111', 'SOPORTE_TECNICO',    N'Soporte tecnico',            1),
    ('22222222-2222-4222-8222-222222222222', 'COORDINACION',       N'Coordinacion interna',       1),
    ('33333333-3333-4333-8333-333333333333', 'INFRAESTRUCTURA',    N'Infraestructura',            1),
    ('44444444-4444-4444-8444-444444444444', 'ATENCION_CIUDADANA', N'Atencion ciudadana',         1),
    ('55555555-5555-4555-8555-555555555555', 'OTROS',              N'Otros',                      1);

-- Categoria inactiva: existe para poder demostrar el codigo de error
-- CATEGORIA_INACTIVA sin tener que desactivar una categoria en uso.
INSERT INTO categoria (id, codigo, nombre, activa) VALUES
    ('66666666-6666-4666-8666-666666666666', 'HISTORICO_ARCHIVADO', N'Historico archivado (inactiva)', 0);
