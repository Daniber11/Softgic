# =============================================================================
#  Servicio de Indicadores — imagen multi-stage
#
#  Un solo modulo Maven (ADR-003): no hay cuatro directorios de codigo fuente
#  que copiar por separado como en Solicitudes, solo pom.xml y src.
# =============================================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S aplicacion && adduser -S aplicacion -G aplicacion
COPY --from=build /app/target/indicadores-service.jar app.jar
RUN chown aplicacion:aplicacion app.jar
USER aplicacion

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --retries=15 --start-period=30s \
  CMD wget -q -O- http://localhost:8082/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=${JVM_MAX_RAM_PERCENTAGE:-60} -jar app.jar"]
