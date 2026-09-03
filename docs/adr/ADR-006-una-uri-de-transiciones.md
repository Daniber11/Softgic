# ADR-006 — Una URI de transiciones en lugar de un endpoint por acción

## Contexto

Una solicitud pasa por varias transiciones de estado: resolver, devolver y cerrar (tomar se
modela aparte, como la creación de una asignación). El diseño de la API debe decidir si cada
transición tiene su propio endpoint (`POST /resolver`, `POST /devolver`, `POST /cerrar`) o si
todas comparten uno solo, discriminado por un campo del cuerpo.

## Decisión

Una única ruta, `POST /api/v1/solicitudes/{id}/transiciones`, con el cuerpo
`{ "accion": "RESOLVER | DEVOLVER | CERRAR", "motivo": "...", "observacion": "..." }`.

## Alternativas consideradas

- **Un endpoint por acción** (`/resolver`, `/devolver`, `/cerrar`). Es una opción legítima y
  más "RESTful" en el sentido de recursos-verbo explícitos, pero multiplica endpoints que hacen
  exactamente lo mismo desde el punto de vista del servidor: buscar el agregado, invocar una
  transición, persistir, publicar el evento. El recurso conceptual no es "resolver" o "cerrar"
  por separado, es *la transición de la solicitud*, con la acción como su discriminante.
- **Un único endpoint** (la decisión tomada). Mantiene la máquina de estados con un único punto
  de entrada en el borde REST, que refleja el único punto de entrada equivalente en el dominio
  (`TransicionarSolicitudService`, con un `switch` exhaustivo sobre el enum `Accion`).

## Consecuencias

- Agregar una transición nueva en el futuro (por ejemplo, "escalar") significa agregar una
  constante al enum `Accion` del dominio y un caso al `switch`; no significa agregar un
  controlador ni una ruta nueva.
- El cliente HTTP (frontend) tiene una única función de mutación para las tres acciones, con la
  acción como parámetro, en vez de tres funciones casi idénticas.
- La documentación OpenAPI describe una ruta con una unión discriminada de payloads en lugar de
  tres rutas separadas; quien lea el contrato por primera vez debe entender que el campo
  `accion` es el que determina la semántica real de la petición.
- Si el reto hubiera exigido explícitamente URIs por acción, esta decisión se habría invertido;
  el blueprint deja constancia de que la alternativa es igual de válida y que esta es la
  justificación de la elegida.
