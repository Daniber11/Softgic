# ADR-011 — Compose único en la raíz sin perfiles

## Contexto

El escenario de aceptación A7 —y el enunciado del reto— exige que el sistema completo arranque
con exactamente `docker compose up --build`. Una versión anterior (v1.0) ubicaba el archivo en
`deploy/compose/` y usaba perfiles `infra` y `full` para separar "solo dependencias" de "stack
completo".

## Decisión

`compose.yaml` vive en la **raíz** del repositorio y **ningún servicio declara `profiles`**.
El comando canónico es exactamente el del enunciado:

```bash
docker compose up --build
```

Para desarrollar contra dependencias sueltas (sin reconstruir las aplicaciones en cada cambio),
se nombran los servicios explícitamente:

```bash
docker compose up -d sqlserver rabbitmq keycloak db-init
```

## Alternativas consideradas

- **`deploy/compose/` con perfiles `infra`/`full`** (v1.0). Descartada por dos fallas reales,
  no cosméticas. Primero, `docker compose up --build` ejecutado desde la raíz —el comando
  exacto del enunciado— no habría encontrado el archivo sin un `-f` explícito: el arranque
  habría fallado en el primer paso, antes de llegar a ningún contenedor. Segundo, un servicio
  con `profiles: [full]` **no arranca** con `docker compose up` sin especificar el perfil: el
  evaluador que siguiera el comando literal del enunciado habría visto solo la infraestructura
  levantada y ninguna de las cuatro aplicaciones, es decir, A7 habría fallado en la práctica
  aunque el archivo "existiera".
- **Mantener perfiles pero documentar el flag `--profile full` en el README**. Descartada:
  añade un paso que el enunciado no pide y que un evaluador siguiendo el comando literal no
  ejecutaría. El criterio de oro del blueprint es explícito: ante cualquier disyuntiva entre
  elegancia de configuración y ejecutabilidad directa, gana la ejecutabilidad.

## Consecuencias

- Un único archivo, sin perfiles, es la superficie de configuración más simple posible: menos
  piezas que puedan fallar en el momento exacto que más pesa en la evaluación.
- El modo "solo dependencias" para desarrollo local no depende de un perfil sino de nombrar los
  servicios explícitamente en la línea de comandos; es un patrón nativo de Compose, sin
  configuración adicional en el archivo.
- Corrección C1 del registro de correcciones del blueprint (v1.0 → v1.1).
