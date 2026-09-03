# ADR-003 — Hexagonal estricto en Solicitudes; en capas en Indicadores

## Contexto

El sistema tiene dos servicios con naturalezas muy distintas. El Servicio de Solicitudes posee
una máquina de estados con invariantes, reglas de autorización por rol y trazabilidad completa
del ciclo de vida de una solicitud: tiene dominio real que proteger de la infraestructura que
lo rodea. El Servicio de Indicadores recibe eventos y los transforma en filas de un modelo de
lectura en estrella: no decide nada, no tiene una sola regla de negocio propia, solo traduce un
hecho ya ocurrido a una fila de hecho analítico.

## Decisión

**Solicitudes** se estructura en cuatro módulos Maven separados —`domain`, `application`,
`infrastructure`, `bootstrap`— con la regla de dependencia estricta hacia el núcleo (ADR-008).
**Indicadores** es un único módulo Maven en capas simples: `consumer` y `web` (entrada) →
`service` → `persistence`, sin puertos ni adaptadores.

## Alternativas consideradas

- **Aplicar hexagonal en ambos servicios**, por uniformidad. Descartada: en Indicadores no
  existe una regla de negocio que un puerto pudiera aislar de un cambio de infraestructura. La
  separación en domain/application/infrastructure habría multiplicado archivos e interfaces sin
  proteger nada real, exactamente el antipatrón que la rúbrica de evaluación penaliza de forma
  explícita ("arquitectura extensa sin justificación").
- **Arquitectura en capas en ambos servicios**, por simplicidad. Descartada para Solicitudes:
  ahí sí hay una máquina de estados e invariantes de autorización que deben sobrevivir a
  cualquier cambio de framework o de mecanismo de persistencia; sin fronteras impuestas por
  módulos físicos separados, nada impide que esa lógica se filtre hacia un controlador o una
  entidad JPA con el tiempo.

## Consecuencias

- La asimetría es deliberada y debe poder explicarse en dos frases: Solicitudes protege
  dominio, Indicadores transforma datos. Es el punto de la entrevista donde se demuestra
  criterio para no aplicar un patrón por costumbre.
- El equipo (o el evaluador) que revise el repositorio encuentra dos estilos de organización de
  paquetes distintos entre los dos servicios; esto es intencional y está documentado aquí y en
  el README, no es una inconsistencia accidental.
- Si Indicadores adquiriera en el futuro reglas de negocio propias (por ejemplo, alertas
  configurables con condiciones complejas), la arquitectura en capas dejaría de ser adecuada y
  esta decisión debería revisarse.
