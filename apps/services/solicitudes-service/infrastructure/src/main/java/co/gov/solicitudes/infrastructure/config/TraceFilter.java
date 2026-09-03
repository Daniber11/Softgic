package co.gov.solicitudes.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propaga o genera el identificador de correlacion.
 *
 * <p>Se ejecuta antes que el filtro de seguridad para que incluso un 401 quede correlacionado. La
 * cabecera se devuelve siempre en la respuesta: sin eso, quien reporta un fallo no tiene como
 * decir cual de miles de peticiones fue la suya.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {

    String recibido = peticion.getHeader(CorrelacionContexto.CABECERA);
    String correlationId =
        (recibido != null && !recibido.isBlank()) ? recibido : CorrelacionContexto.generar();

    CorrelacionContexto.establecer(correlationId);
    respuesta.setHeader(CorrelacionContexto.CABECERA, correlationId);

    try {
      cadena.doFilter(peticion, respuesta);
    } finally {
      // Imprescindible: los hilos del contenedor se reutilizan y un MDC sin limpiar
      // etiquetaria la siguiente peticion con la correlacion de la anterior.
      CorrelacionContexto.limpiar();
    }
  }
}
