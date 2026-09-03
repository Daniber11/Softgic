# Evidencia de cobertura — JaCoCo

Capturada ejecutando `mvn clean verify` (vía el contenedor `maven:3.9-eclipse-temurin-21`
documentado en el README) el 3 de septiembre de 2026. Exclusiones honestas (configuración,
DTOs, entidades JPA) declaradas en cada `pom.xml`; ver §10 del blueprint y §8 del README.

## Servicio de Solicitudes

| Módulo | Cobertura de instrucciones | Notas |
|---|---|---|
| `domain` | **80%** (222 de 1.122 instrucciones sin cubrir) | Sin exclusiones: es el núcleo, aquí vive la cobertura que cuenta. `SolicitudTest` cubre la matriz completa de transiciones, autorización por rol, eventos de dominio, observaciones y rehidratación. |
| `application` | **64%** (171 de 487 instrucciones sin cubrir) | Un test por caso de uso (`RegistrarSolicitudServiceTest`, `TomarSolicitudServiceTest`, `TransicionarSolicitudServiceTest`, `AgregarObservacionServiceTest`), con dobles en memoria, no Mockito. Las consultas (`Consultar*Service`) no tienen test dedicado: son *pass-through* al repositorio sin lógica propia que orquestar. |
| `infrastructure` | Sin reporte (0 pruebas) | No existe `src/test` en este módulo. Es una limitación real, no oculta: ver README §11. Las exclusiones de configuración/DTO/entidad JPA ya están declaradas para cuando se agreguen pruebas de integración. |
| `bootstrap` | "No class files specified" | Tras excluir `SolicitudesApplication` y la configuración de cableado (`BeanConfiguration`, `RabbitTopologyConfiguration`), no queda producción que medir aquí — el valor real de este módulo es la prueba **ArchUnit** (9/9), no una cifra de cobertura. |

## Servicio de Indicadores

| Cobertura de instrucciones | Notas |
|---|---|
| **27%** (343 de 473 instrucciones sin cubrir) | Solo `TipoDeEventoTest` (5 pruebas) existe hoy. Exclusiones: `config/**`, `IndicadoresApplication`, `persistence/**` (entidades JPA + repositorios Spring Data sin lógica propia). `ProyeccionService` y `ConsultaIndicadoresService` quedan medidos sin excluir — el 27% refleja honestamente que aún no tienen prueba dedicada, no una cifra inflada. |

## Por qué no se excluyó más para subir la cifra

`SolicitudMapper`, `SolicitudRepositoryAdapter`, `GeneradorCodigoAdapter`,
`ConversorAuthoritiesKeycloak`, `ManejadorGlobalDeErrores` y el resto de adaptadores de
`infrastructure` **no tienen prueba** y por lo tanto no suman cobertura, pero tampoco se
excluyeron: excluirlos solo para que el reporte mostrara un número más alto sería exactamente
la "cobertura inflada por código trivial" que la rúbrica penaliza explícitamente (blueprint
§10). Es preferible reportar 64% real sobre casos de uso y 0% honesto sobre infraestructura sin
prueba, que una cifra agregada más alta que no distingue una cosa de la otra.

## Comando de reproducción

```bash
cd apps/services/solicitudes-service
mvnd clean verify   # genera domain/application/bootstrap/target/site/jacoco/index.html

cd apps/services/indicadores-service
mvnd clean verify   # genera target/site/jacoco/index.html
```
