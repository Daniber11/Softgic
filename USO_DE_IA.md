# Uso de asistentes de IA

> Entregable exigido por la prueba técnica. Se completa durante la construcción.
> Las secciones marcadas con `⟨…⟩` deben rellenarse con hechos reales, no con estimaciones.

---

## 1. Herramientas utilizadas

| Herramienta | Uso |
|---|---|
| Claude Code | Implementación asistida bajo especificación previa |
| ⟨otras⟩ | ⟨…⟩ |

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
| Estructura de módulos Maven | ⟨…⟩ | |
| Implementación del agregado y la máquina de estados | ⟨…⟩ | Pruebas escritas antes que la implementación |
| Adaptadores JPA y mappers | ⟨…⟩ | Código repetitivo, alto apoyo |
| Publicador Outbox | ⟨…⟩ | Revisado línea a línea por el riesgo de pérdida de eventos |
| Configuración de seguridad | ⟨…⟩ | Revisada línea a línea |
| Configuración de Module Federation | ⟨…⟩ | Requirió depuración manual |
| Migraciones SQL | ⟨…⟩ | |
| Componentes React y vistas | ⟨…⟩ | |
| Manifiestos Helm y pipeline | ⟨…⟩ | |
| Documentación y diagramas | ⟨…⟩ | |

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

⟨Listar aquí lo que quedó diseñado pero no implementado, con la razón y el trabajo pendiente.
Ser explícito: el reto penaliza más una promesa incumplida que una limitación declarada.⟩

---

## 7. Declaración

Comprendo, puedo explicar y puedo modificar todo el código entregado. Las decisiones de
arquitectura son propias y están justificadas en `docs/adr/`. No se incluyeron datos, logos,
URLs internas, tokens ni credenciales reales de ningún cliente. Las credenciales presentes en
el repositorio son ficticias, locales y están rotuladas como tales.
