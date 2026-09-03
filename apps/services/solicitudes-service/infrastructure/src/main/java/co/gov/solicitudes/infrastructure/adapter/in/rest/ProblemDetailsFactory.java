package co.gov.solicitudes.infrastructure.adapter.in.rest;

import co.gov.solicitudes.infrastructure.config.CorrelacionContexto;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Construccion uniforme de errores segun RFC 9457.
 *
 * <p>Centralizarla en un unico lugar es lo que garantiza que todos los errores del API tengan la
 * misma forma. Si cada manejador armara su respuesta, tarde o temprano alguno filtraria un mensaje
 * de Hibernate o un nombre de tabla.
 *
 * <p>El campo {@code codigo} es el contrato estable con el frontend: puede ramificar sobre el sin
 * leer el texto, que esta redactado para personas y puede cambiar sin previo aviso.
 */
public final class ProblemDetailsFactory {

  private static final String BASE_TIPOS = "https://api.local/errors/";

  private ProblemDetailsFactory() {
    // Utilidad.
  }

  public static ProblemDetail crear(HttpStatus estado, String titulo, String detalle, String codigo) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
    problema.setTitle(titulo);
    problema.setType(URI.create(BASE_TIPOS + aSlug(codigo)));
    problema.setProperty("codigo", codigo);
    problema.setProperty("correlationId", CorrelacionContexto.actual());
    problema.setProperty("timestamp", Instant.now().toString());
    return problema;
  }

  /** CONFLICTO_CONCURRENCIA -> conflicto-concurrencia, para que el type sea legible como URI. */
  private static String aSlug(String codigo) {
    return codigo.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
  }
}
