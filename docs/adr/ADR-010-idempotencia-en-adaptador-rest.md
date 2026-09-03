# ADR-010 — Idempotencia HTTP resuelta en el adaptador REST, no como puerto de aplicación

## Contexto

`POST /api/v1/solicitudes` acepta un header `Idempotency-Key` para que un reintento de red del
cliente (timeout, doble clic, retry automático) no produzca una solicitud duplicada. La versión
1.0 de este blueprint modelaba esto como `IdempotenciaPort`, un puerto de salida de la capa
`application`, implementado por un adaptador de infraestructura como cualquier otro puerto de
persistencia.

## Decisión

Se elimina `IdempotenciaPort`. La idempotencia HTTP se resuelve **íntegramente en el adaptador
REST**, mediante un interceptor (`FiltroIdempotencia`) y la tabla `idempotencia_comando`
(llave, hash del cuerpo, respuesta emitida, expiración). Los casos de uso de `application`
**ignoran por completo** que este mecanismo existe.

## Alternativas consideradas

- **`IdempotenciaPort` como puerto de salida de `application`** (v1.0). Descartada: `Idempotency-Key`
  es un detalle del **protocolo de transporte** HTTP —un header—, no una regla del dominio de
  negocio. Modelarlo como puerto habría obligado a la capa de aplicación, que debe permanecer
  independiente de cómo llega una petición, a conocer un concepto que solo tiene sentido si el
  transporte es HTTP. Si mañana un comando llegara por un canal distinto (un consumidor de cola,
  una llamada interna), ese puerto no tendría ningún significado ahí, y su presencia en
  `application` sería una fuga de un detalle de REST hacia el núcleo.
- **Idempotencia dentro del propio caso de uso `RegistrarSolicitudService`**, comprobando la
  llave antes de ejecutar la lógica de negocio. Descartada por la misma razón: mezclaría una
  preocupación de transporte con la orquestación del dominio, y forzaría a cada caso de uso que
  quisiera ser idempotente a repetir esa lógica.

## Consecuencias

- El módulo `application` queda más limpio: ninguno de sus puertos ni comandos menciona
  `Idempotency-Key`, cabeceras HTTP ni nada del protocolo de transporte.
- El interceptor vive enteramente en `infrastructure/adapter/in/rest`, junto al resto de
  preocupaciones del borde HTTP (DTOs, manejo de errores, extracción del actor del JWT).
- Un reintento con la misma llave y el mismo cuerpo devuelve la respuesta original sin volver a
  ejecutar el caso de uso; la misma llave con un cuerpo distinto devuelve `409`, porque el
  cliente está reutilizando una llave para una operación diferente.
- Corrección C6 del registro de correcciones del blueprint (v1.0 → v1.1).
