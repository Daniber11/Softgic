package co.gov.solicitudes.application.port.out;

import java.time.Instant;

/**
 * Fuente de tiempo del sistema.
 *
 * <p>Existe para que ni el dominio ni los casos de uso llamen a Instant.now(). Una prueba que no
 * controla el reloj no es determinista: la unica forma de afirmar que una transicion quedo
 * fechada correctamente es inyectar el instante.
 */
public interface RelojPort {
  Instant ahora();
}
