package co.gov.solicitudes.infrastructure.config;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Identificador de correlacion de la peticion en curso.
 *
 * <p>Se apoya en el MDC de SLF4J, que ya es por hilo, en lugar de crear otro ThreadLocal: asi el
 * mismo valor aparece automaticamente en cada linea de log estructurado y ademas puede leerse para
 * ponerlo en el sobre del evento. Ese es el hilo que permite seguir una operacion desde la
 * peticion HTTP hasta la proyeccion en el servicio de indicadores.
 */
public final class CorrelacionContexto {

  public static final String CLAVE_MDC = "correlationId";
  public static final String CABECERA = "X-Correlation-Id";

  private CorrelacionContexto() {
    // Utilidad.
  }

  public static void establecer(String correlationId) {
    MDC.put(CLAVE_MDC, correlationId);
  }

  public static void limpiar() {
    MDC.remove(CLAVE_MDC);
  }

  /** Nunca devuelve nulo: si no hay contexto, genera uno para no perder la traza. */
  public static String actual() {
    String valor = MDC.get(CLAVE_MDC);
    return valor != null ? valor : UUID.randomUUID().toString();
  }

  public static String generar() {
    return UUID.randomUUID().toString();
  }
}
