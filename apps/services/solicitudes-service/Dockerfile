# =============================================================================
#  Servicio de Solicitudes — imagen multi-stage
#
#  Stage "build": compila los cuatro modulos Maven (domain, application,
#  infrastructure, bootstrap) y empaqueta el jar ejecutable. Los pom.xml se
#  copian antes que el codigo fuente para que Docker cachee la descarga de
#  dependencias mientras solo cambie el codigo, no las versiones.
#
#  Stage runtime: JRE (no JDK) sobre Alpine, usuario no root, sin Maven ni
#  codigo fuente — solo el jar. HEALTHCHECK contra Actuator, el mismo
#  endpoint que usa docker compose para encadenar el arranque (A7).
# =============================================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
COPY bootstrap/pom.xml bootstrap/
RUN mvn -B -q -pl domain,application,infrastructure,bootstrap dependency:go-offline || true

COPY domain domain
COPY application application
COPY infrastructure infrastructure
COPY bootstrap bootstrap
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S aplicacion && adduser -S aplicacion -G aplicacion
COPY --from=build /app/bootstrap/target/solicitudes-service.jar app.jar
RUN chown aplicacion:aplicacion app.jar
USER aplicacion

EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=5s --retries=15 --start-period=30s \
  CMD wget -q -O- http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1

# MaxRAMPercentage en lugar de -Xmx fijo: el limite se calcula sobre la memoria
# que Docker le asigna al contenedor (mem_limit en compose), no sobre la del
# anfitrion completo, evitando que la JVM sobre-reserve en un entorno acotado.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=${JVM_MAX_RAM_PERCENTAGE:-60} -jar app.jar"]
