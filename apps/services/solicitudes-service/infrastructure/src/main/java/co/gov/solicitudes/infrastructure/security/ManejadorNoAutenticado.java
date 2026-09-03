package co.gov.solicitudes.infrastructure.security;

import co.gov.solicitudes.infrastructure.adapter.in.rest.ProblemDetailsFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Responde 401 con el mismo formato de error que el resto del API.
 *
 * <p>Sin este manejador, Spring Security devolveria una respuesta vacia con la cabecera
 * WWW-Authenticate, que obligaria al frontend a tratar los errores de autenticacion como un caso
 * aparte del resto.
 *
 * <p>El detalle es deliberadamente generico: distinguir "token expirado" de "firma invalida" le
 * diria a un atacante en que parte del proceso fallo.
 */
public class ManejadorNoAutenticado implements AuthenticationEntryPoint {

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

  public ManejadorNoAutenticado(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public void commence(
      HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException fallo)
      throws IOException {

    ProblemDetail problema =
        ProblemDetailsFactory.crear(
            HttpStatus.UNAUTHORIZED,
            "No autenticado",
            "Se requiere un token de acceso valido.",
            "TOKEN_INVALIDO");
    problema.setInstance(java.net.URI.create(peticion.getRequestURI()));

    respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
    respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    respuesta.setCharacterEncoding("UTF-8");
    json.writeValue(respuesta.getOutputStream(), problema);
  }
}
