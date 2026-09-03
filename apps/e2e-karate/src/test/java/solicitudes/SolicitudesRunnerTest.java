package solicitudes;

import com.intuit.karate.junit5.Karate;

/**
 * Ejecuta todos los .feature de este paquete contra el stack levantado por
 * `docker compose up --build`: A1 (registro valido, incluida la idempotencia
 * HTTP), A3 (rol insuficiente sin efectos) y el recorrido completo
 * REGISTRADA -> EN_ATENCION -> RESUELTA, con A4 al final del mismo.
 */
class SolicitudesRunnerTest {

  @Karate.Test
  Karate ejecutarSuiteDeAceptacion() {
    return Karate.run("classpath:solicitudes").relativeTo(getClass());
  }
}
