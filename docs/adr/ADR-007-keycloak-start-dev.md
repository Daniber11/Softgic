# ADR-007 — Keycloak en `start-dev` con H2 embebida para local

## Contexto

Keycloak necesita persistencia propia (realms, clientes, usuarios). En producción eso implica
una base de datos externa (PostgreSQL típicamente) y el modo `start` (no `start-dev`), con TLS
y hostname estrictos. Este proyecto es un entorno de demostración local que debe arrancar con
un único comando (`docker compose up --build`, escenario A7) y sin pasos de configuración
manual.

## Decisión

Keycloak 26 corre con `start-dev --import-realm`, usando su base **H2 embebida** (no una base
externa), y el realm `solicitudes-gov` —roles, dos clientes, cinco usuarios de prueba— se
importa automáticamente desde el JSON exportado en `deploy/keycloak/` en cada arranque.

## Alternativas consideradas

- **Una base PostgreSQL dedicada para Keycloak**, en modo `start` de producción. Descartada
  para este entorno: añade un contenedor más al Compose y otra dependencia que encadenar en los
  healthchecks, sin ningún beneficio para un realm que se reimporta desde JSON en cada arranque
  de todas formas —la persistencia entre reinicios no aporta nada aquí, porque el estado
  "correcto" siempre es el del archivo versionado, no el que Keycloak acumuló en ejecuciones
  anteriores—.
- **Configurar manualmente el realm a través de la consola de administración**, documentando
  los pasos en el README. Descartada: rompe el criterio de "un solo comando" (A7) y no es
  reproducible de forma determinista entre evaluadores.

## Consecuencias

- El estado de Keycloak no sobrevive a un `docker compose down -v`; esto es deseable aquí, no
  una limitación: cada arranque parte del realm versionado en el repositorio, sin deriva de
  configuración entre ejecuciones.
- `start-dev` no está pensado para producción (deshabilita ciertas verificaciones de hostname y
  TLS estrictos); esto se documenta explícitamente como una decisión de entorno local, no como
  una recomendación de despliegue real.
- El realm exportado (`realm-solicitudes-gov.json`) es el único lugar donde viven roles,
  clientes y usuarios de prueba; cualquier cambio de configuración de identidad se hace editando
  ese archivo, no a mano en la consola.
