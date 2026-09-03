package co.gov.indicadores.config;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Identificador de correlacion de la operacion en curso.
 *
 * <p>Llega por dos caminos distintos y ambos importan: en las peticiones HTTP por la cabecera
 * X-Correlation-Id, y en los eventos dentro del sobre que escribio el productor. Trasladarlo al
 * MDC en los dos casos es lo que permite seguir una operacion completa —registro de la solicitud,
 * publicacion del evento, proyeccion analitica— con un unico identificador a traves de los logs de
 * los dos servicios.
 */
public final class CorrelacionContexto {

  public static final String CLAVE_MDC = "correlationId";
  public static final String CABECERA = "X-Correlation-Id";

  private CorrelacionContexto() {
    // Utilidad.
  }

  public static void establecer(String correlationId) {
    MDC.put(CLAVE_MDC, correlationId != null ? correlationId : generar());
  }

  public static void limpiar() {
    MDC.remove(CLAVE_MDC);
  }

  public static String generar() {
    return UUID.randomUUID().toString();
  }
}
