# Plataforma de Gestión de Solicitudes Operacionales

Solución a la prueba técnica Full Stack: arquitectura hexagonal, microservicios,
eventos y microfrontends.

> **Estado:** en construcción. Fase 1 (infraestructura) completada y verificada.
> Este README se completa con la estructura exigida —resumen · arquitectura · ADR ·
> seguridad · contratos · modelo de datos · ejecución local · pruebas · CI/CD ·
> observabilidad · limitaciones— en la fase 6.

---

## Prerrequisitos

| Requisito | Versión verificada |
|---|---|
| Docker Engine | 24.0.7 |
| Docker Compose | v2.23.3 |

No hace falta instalar Java, Maven, Node, pnpm ni Helm en la máquina: todo el
*toolchain* se ejecuta dentro de contenedores.

---

## Arranque

```bash
cp .env.example .env
docker compose up --build
```

Ese es el único comando necesario. Para levantar solo las dependencias mientras se
desarrollan los servicios por fuera:

```bash
docker compose up -d sqlserver rabbitmq keycloak db-init
```

### Validación del estado de los servicios

```bash
docker compose ps
```

Se espera `(healthy)` en `sqlserver`, `rabbitmq` y `keycloak`. El contenedor
`db-init` es de un solo uso y debe aparecer como `Exited (0)`: crea las dos bases
de datos y termina.

```bash
curl -s http://localhost:8080/realms/solicitudes-gov/.well-known/openid-configuration
```

---

## Puertos

| Puerto | Servicio |
|---|---|
| 1433 | SQL Server 2022 |
| 5672 | RabbitMQ (AMQP) |
| 15672 | RabbitMQ (consola de administración) |
| 8080 | Keycloak |
| 8081 | Servicio de Solicitudes *(fase 3)* |
| 8082 | Servicio de Indicadores *(fase 4)* |
| 3000 | Shell (host de microfrontends) *(fase 2)* |
| 3001 | MFE Indicadores *(fase 2)* |

---

## Usuarios de prueba

> **Credenciales ficticias, locales y de demostración.** No corresponden a ninguna
> persona ni a ningún sistema real. Se documentan a propósito: son parte del
> entregable, no un secreto filtrado.

Contraseña común: `Demo#2026`

| Usuario | Rol | Para qué existe |
|---|---|---|
| `solicitante1` | SOLICITANTE | Registra solicitudes y consulta solo las propias |
| `analista1` | ANALISTA | Toma, observa y resuelve |
| `analista2` | ANALISTA | Segundo analista: permite demostrar **A2**, dos analistas compitiendo por la misma solicitud |
| `supervisor1` | SUPERVISOR | Consulta todo, devuelve y cierra |
| `sinrol1` | *(ninguno)* | Permite demostrar **A3**: 403 sin efectos persistidos |

Administrador de Keycloak: `admin` / `Local#Keycloak2026` en <http://localhost:8080>.
Consola de RabbitMQ: `solicitudes_local` / `Local#Rabbit2026` en <http://localhost:15672>.

### Obtener un token para pruebas manuales

El cliente `karate-e2e` existe **exclusivamente para pruebas automatizadas** y es el
único con *direct grant* habilitado. El cliente del navegador, `shell-web`, usa
Authorization Code + PKCE y rechaza este flujo a propósito.

```bash
curl -s -X POST http://localhost:8080/realms/solicitudes-gov/protocol/openid-connect/token -d "client_id=karate-e2e" -d "username=analista1" -d "password=Demo#2026" -d "grant_type=password"
```

---

## Variables de entorno

Todas las credenciales viven en `.env`, que **no se versiona**. El archivo
`.env.example` contiene la lista completa con valores ficticios y es el que se copia
al clonar.

---

## Limitaciones y trabajo pendiente

Se documentan en la fase 6, de forma explícita y honesta, incluyendo lo que quedó
diseñado pero no implementado.
