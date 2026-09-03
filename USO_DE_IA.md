# Uso de asistentes de IA

> Entregable exigido por la prueba técnica. Se completa durante la construcción.
> Las secciones marcadas con `⟨…⟩` deben rellenarse con hechos reales, no con estimaciones.

---

## 1. Herramientas utilizadas

| Herramienta | Uso |
|---|---|
| Claude Code (Sonnet 5) | Implementación asistida bajo especificación previa, en seis sesiones (una por fase) |
| Navegador integrado de Claude Code | Verificación manual en un navegador real de cada flujo del frontend (login, bandeja, detalle, acciones por rol, accesibilidad por teclado) antes de declarar una fase terminada |
| Herramientas de shell (Docker, Maven en contenedor, pnpm) | Ejecutadas por el propio asistente para compilar, probar y verificar, nunca simuladas |

---

## 2. Cómo se estructuró el trabajo

El proyecto no se construyó pidiendo código de forma conversacional. Se aplicó una
arquitectura de trabajo asistido con seis mecanismos deliberados:

**Especificación primero.** El documento `BLUEPRINT.md` fija stack, contratos HTTP, contrato
de eventos, modelo de datos y política de seguridad *antes* de generar la primera línea de
código. El asistente implementa una especificación existente; no improvisa una.

**Constitución persistente.** El archivo `CLAUDE.md` se carga en cada sesión con reglas
marcadas como bloqueantes: prohibición de dependencias en el módulo de dominio, prohibición
de secretos en el repositorio, prohibición de fijar versiones sin verificar. Esto contiene la
deriva arquitectónica que aparece en sesiones largas.

**Fases con compuerta de verificación.** El trabajo se dividió en seis fases y ninguna avanzó
sin un comando ejecutado y su salida real. La regla explícita fue que "debería funcionar" no
constituye verificación.

**Restricciones convertidas en pruebas.** Una prueba ArchUnit verifica la regla de
dependencia y rompe el build si alguien introduce un import de framework en el núcleo. La
integridad arquitectónica dejó de depender de la disciplina del generador y pasó a ser una
condición del build.

**Autorrevisión con rol explícito.** Antes de cerrar cada tarea, el asistente cambia a rol de
revisor y audita su propio código contra una checklist y una lista de antipatrones.

**Bitácora como memoria.** `BITACORA.md` mantiene continuidad entre sesiones sin depender de
la ventana de contexto.

---

## 3. Actividades apoyadas por IA

| Actividad | Grado de apoyo | Observación |
|---|---|---|
| Estructura de módulos Maven | Alto | Generada siguiendo la regla de dependencia del blueprint; verificada compilando y con ArchUnit, no solo por inspección visual |
| Implementación del agregado y la máquina de estados | Alto | Pruebas escritas antes que la implementación (`SolicitudTest`); la tabla de transiciones (`Accion`) es la única fuente de verdad, no hay lógica duplicada |
| Adaptadores JPA y mappers | Alto | Código repetitivo, alto apoyo |
| Publicador Outbox | Alto, con revisión propia adicional | Revisado línea a línea por el riesgo de pérdida de eventos; el defecto real de idempotencia (`save()` en vez de `INSERT` nativo) se encontró ejecutando el escenario A5 con datos reales, no por inspección de código |
| Configuración de seguridad | Alto, con revisión propia adicional | Revisada línea a línea; las tres capas de defensa en profundidad (filtro, `@PreAuthorize`, dominio) se probaron manualmente con el usuario `sinrol1` |
| Configuración de Module Federation | Medio | Requirió depuración manual extensa en el navegador: duplicación de `UserManager` entre shell y remoto, una carrera de hidratación del `authBridge`, y una carrera entre el token y Redux en el primer fetch del remoto — ninguno de estos tres defectos era visible por inspección de código, se encontraron inspeccionando el runtime de Module Federation en la consola del navegador |
| Migraciones SQL | Alto | |
| Componentes React y vistas | Alto, con verificación en navegador real | Cada vista se probó manualmente con los cinco usuarios de prueba antes de declarar la fase terminada; un defecto real de foco en el diálogo de observaciones (`autoFocus` no funcionaba con `TextField` multilínea) se encontró navegando por teclado, no leyendo el código |
| Manifiestos Helm y pipeline | Alto, sin ejecución contra infraestructura real | `helm lint` y `helm template` se ejecutaron y verificaron contra los tres conjuntos de valores; el `.gitlab-ci.yml` se validó por sintaxis YAML, no contra un GitLab real (ver limitaciones) |
| Documentación y diagramas | Alto | Los diagramas Mermaid se validaron sintácticamente de forma programática antes de darlos por completos |

---

## 4. Verificaciones humanas realizadas

- Ejecución local completa del flujo principal con los usuarios de prueba de cada rol.
- Comprobación manual de los escenarios A1 a A7 y registro de la evidencia.
- Revisión línea a línea de la configuración de seguridad y del publicador Outbox, por ser
  los dos puntos donde un error silencioso resultaría más costoso.
- Verificación de que ninguna versión de dependencia fijada fuera inventada.
- Auditoría de que no existan secretos, datos personales ni referencias reales del cliente
  en el repositorio ni en el historial de Git.
- Comprobación de que el módulo `domain` no declara dependencias de producción, tanto por
  inspección del `pom.xml` como por la prueba ArchUnit.
- Prueba de arranque desde un clon limpio siguiendo únicamente el README.

---

## 5. Decisiones propias, no delegadas

Estas decisiones se tomaron por criterio de ingeniería propio y están registradas como ADR:

| Decisión | Razón |
|---|---|
| RabbitMQ sobre Kafka | Presupuesto de memoria en el entorno local y demostrabilidad visual de reintentos y DLQ durante la evaluación |
| Arquitectura hexagonal solo en el Servicio de Solicitudes | Indicadores es un proyector sin reglas de negocio; aplicar hexagonal allí sería ceremonia sin beneficio |
| Bloqueo optimista en lugar de pesimista | Baja contención esperada y comportamiento determinista y testeable para el escenario A2 |
| Dimensión rol en lugar de persona en el modelo analítico | Minimización de datos personales replicados |
| Una URI de transiciones en lugar de un endpoint por acción | Mantiene la máquina de estados con un único punto de entrada |
| Distinción semántica entre 409 y 422 | 409 indica que el estado cambió y conviene reintentar; 422 indica que la operación nunca fue posible |
| Política de recorte de alcance | La rúbrica premia lo demostrable sobre lo extenso |

---

## 6. Limitaciones asumidas

La lista completa, con la razón de cada una, vive en el README (§11 "Limitaciones y trabajo
pendiente"), para que sea un único lugar de verdad. Resumen:

- Sin Prometheus/Grafana en el Compose (Actuator ya expone `/actuator/prometheus`, listo para
  ser scrapeado por un Prometheus externo).
- Storybook cubre exactamente los dos componentes exigidos, no el resto del frontend.
- Sin pruebas de integración con Testcontainers; la concurrencia (A2) y la idempotencia (A5) se
  verificaron manualmente y quedan como evidencia en `docs/evidencias/`, no automatizadas
  contra una base real en pipeline.
- El chart de Helm se validó con `helm lint`/`helm template`, no con un despliegue real contra
  un clúster Kubernetes.
- `outbox_evento` no tiene purga automatizada.
- Riesgo de colisión de `GeneradorCodigoAdapter` bajo concurrencia muy alta, mitigado solo por
  la restricción `UNIQUE` de base de datos, declarado como riesgo abierto y no como defecto
  oculto.
- El `.gitlab-ci.yml` no se ejecutó contra un GitLab real: se validó por sintaxis YAML y
  revisión manual de cada etapa.

---

## 7. Declaración

Comprendo, puedo explicar y puedo modificar todo el código entregado. Las decisiones de
arquitectura son propias y están justificadas en `docs/adr/`. No se incluyeron datos, logos,
URLs internas, tokens ni credenciales reales de ningún cliente. Las credenciales presentes en
el repositorio son ficticias, locales y están rotuladas como tales.
