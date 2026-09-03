package co.gov.solicitudes.infrastructure.security;

import co.gov.solicitudes.infrastructure.adapter.in.rest.ProblemDetailsFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Responde 403 cuando el token es valido pero el rol no alcanza.
 *
 * <p>Es el escenario A3. Que la respuesta se produzca en el filtro, antes de llegar al
 * controlador, es justamente lo que garantiza que no se persista nada ni se emita ningun evento.
 */
public class ManejadorAccesoDenegado implements AccessDeniedHandler {

  /**
   * Se recibe el ObjectMapper que configura Spring Boot, no uno nuevo.
   *
   * <p>Un ObjectMapper recien construido no lleva registrado el mixin de ProblemDetail, de modo
   * que serializa los campos extendidos anidados bajo "properties" en lugar de al nivel raiz.
   * El resultado seria que los errores de seguridad tuvieran una forma distinta de los del resto
   * del API y el cliente necesitara dos parsers. Se detecto comparando las respuestas reales de
   * un 403 y un 422.
   */
  private final ObjectMapper json;

  public ManejadorAccesoDenegado(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public void handle(
      HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException fallo)
      throws IOException {

    ProblemDetail problema =
        ProblemDetailsFactory.crear(
            HttpStatus.FORBIDDEN,
            "Autorizacion insuficiente",
            "Su rol no permite ejecutar esta operacion.",
            "ACCION_NO_PERMITIDA");
    problema.setInstance(java.net.URI.create(peticion.getRequestURI()));

    respuesta.setStatus(HttpStatus.FORBIDDEN.value());
    respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    respuesta.setCharacterEncoding("UTF-8");
    json.writeValue(respuesta.getOutputStream(), problema);
  }
}
