# ADR-008 — Módulos Maven separados como mecanismo de frontera

## Contexto

La regla de dependencia (`bootstrap → infrastructure → application → domain`, nunca al revés)
es el punto arquitectónico más evaluado del reto (20 de 100 puntos, según el blueprint). Una
convención de nombres de paquete (`co.gov.solicitudes.domain`, `co.gov.solicitudes.application`,
...) dentro de un único módulo es fácil de violar sin que nada lo impida: un `import` de
`org.springframework.*` dentro de una clase del paquete `domain` compila igual que cualquier
otro.

## Decisión

El Servicio de Solicitudes se divide en **cuatro módulos Maven físicamente independientes**:
`domain`, `application`, `infrastructure`, `bootstrap`, cada uno con su propio `pom.xml`. El
`pom.xml` de `domain` no declara ninguna dependencia de producción: ni Spring, ni JPA, ni
Jackson, ni Lombok. Una prueba ArchUnit adicional, alojada en `bootstrap` (el único módulo que
ve las cuatro capas a la vez), verifica en cada build que ningún import cruce la frontera en el
sentido prohibido.

## Alternativas consideradas

- **Un solo módulo con paquetes por capa**, apoyado únicamente en la disciplina del equipo y en
  revisión de código. Descartada: no falla el build. Un import indebido de `domain` hacia
  `org.springframework.data.jpa` compilaría sin ningún aviso, y la "arquitectura hexagonal"
  quedaría en los nombres de las carpetas, no en una garantía verificable —exactamente lo que
  la rúbrica indica que no basta—.
- **Módulos separados sin la prueba ArchUnit**, confiando solo en que `domain` no declare
  dependencias en su `pom.xml`. Insuficiente por sí sola: nada impide agregar una dependencia al
  `pom.xml` de `domain` más adelante, bajo presión de tiempo, para resolver un problema puntual.
  La prueba ArchUnit es la que convierte la regla en algo que **rompe el build** si se viola,
  independientemente de qué dependencias declare el `pom.xml` en ese momento.

## Consecuencias

- La separación física es más verbosa de configurar (cuatro `pom.xml`, un `dependencyManagement`
  en el padre) que un único módulo con paquetes.
- A cambio, la regla de dependencia deja de ser una convención y pasa a ser una propiedad
  verificable del build: `domain` **no puede** compilar con un import de Spring, y ArchUnit
  falla el build si alguna otra capa intenta invertir el sentido de la dependencia.
- El Servicio de Indicadores, por contraste, es un único módulo (ADR-003): la misma decisión de
  "frontera física" no se aplicó ahí porque no hay una frontera de dominio real que proteger.
