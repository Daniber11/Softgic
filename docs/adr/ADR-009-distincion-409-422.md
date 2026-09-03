# ADR-009 — Distinción semántica entre 409 y 422

## Contexto

Dos escenarios de error distintos podrían, superficialmente, mapearse al mismo código HTTP:
que otro analista ya haya tomado la solicitud (A2) y que se intente una transición que la
máquina de estados nunca permite, como `RESUELTA → REGISTRADA` (A4). Usar el mismo código para
ambos obligaría al cliente a inspeccionar el cuerpo del error para decidir si tiene sentido
reintentar, perdiendo la señal que el propio código HTTP debería dar.

## Decisión

**409 Conflict** significa "el estado cambió bajo tus pies mientras decidías: reintenta, quizás
ahora tenga sentido". Se usa cuando el bloqueo optimista detecta una escritura concurrente
(A2). **422 Unprocessable Entity** significa "lo que pediste nunca fue posible, independiente
de cuándo lo pidas: no reintentes tal cual". Se usa cuando la tabla de transiciones del enum
`Accion` no contiene la transición solicitada desde el estado actual (A4).

## Alternativas consideradas

- **Usar 409 para ambos casos.** Es defendible en abstracto —los dos son "conflictos" en un
  sentido amplio—, pero borra la distinción operativa entre "reintenta" y "no reintentes". Un
  cliente automatizado (o un usuario humano frente a un botón de reintentar) necesita saber
  cuál de las dos cosas ocurrió.
- **Usar 400 Bad Request para la transición inválida.** Descartada: 400 comunica un error
  *sintáctico* de la petición (campo faltante, tipo inválido, UUID mal formado — ver la tabla de
  semántica de códigos del blueprint, §5.2). Una transición inválida no es un error sintáctico:
  el cuerpo de la petición es perfectamente válido, lo que falla es una regla de negocio sobre
  el estado actual del recurso, que es exactamente lo que 422 comunica en la especificación
  HTTP.

## Consecuencias

- El frontend puede diferenciar la experiencia de usuario: ante un 409 (A2), tiene sentido
  ofrecer "refrescar y reintentar"; ante un 422 (A4), ese botón sería engañoso, porque
  reintentar la misma operación fallará siempre de la misma forma.
- Esta distinción debe poder explicarse en la entrevista con la frase corta del blueprint:
  "409 es que reintentes, 422 es que no reintentes".
- Ambos casos usan el mismo formato de error, RFC 9457 Problem Details, con `status` reflejando
  el código HTTP real y un `codigo` de catálogo propio (`CONFLICTO_CONCURRENCIA` o
  `TRANSICION_INVALIDA`) para que el cliente no tenga que parsear el `title` en texto libre.
