package co.gov.solicitudes.infrastructure.adapter.in.rest;

import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.Rol;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Traduce el token en un Actor del dominio.
 *
 * <p><b>La identidad se toma del claim {@code sub}, nunca del cuerpo de la peticion.</b> Aceptar
 * un solicitanteId enviado por el cliente permitiria actuar en nombre de otra persona con solo
 * cambiar un campo del JSON.
 *
 * <p>Cuando el token trae varios roles del sistema se elige el de mayor alcance. Es una decision
 * consciente: el modelo de dominio asume un unico rol por accion, y con esta regla un supervisor
 * que ademas sea analista actua siempre con el rol mas amplio, que es lo esperable.
 */
@Component
public class ActorExtractor {

  private static final String PREFIJO_ROL = "ROLE_";

  /** Orden de precedencia, de mayor a menor alcance. */
  private static final Rol[] PRECEDENCIA = {Rol.SUPERVISOR, Rol.ANALISTA, Rol.SOLICITANTE};

  public Actor extraer(JwtAuthenticationToken autenticacion) {
    Jwt jwt = autenticacion.getToken();
    String sujeto = jwt.getSubject();

    Rol rol =
        rolDeMayorAlcance(autenticacion)
            .orElseThrow(
                () ->
                    new org.springframework.security.access.AccessDeniedException(
                        "El token no contiene ningun rol del sistema."));

    return new Actor(sujeto, rol);
  }

  private Optional<Rol> rolDeMayorAlcance(JwtAuthenticationToken autenticacion) {
    for (Rol candidato : PRECEDENCIA) {
      boolean loTiene =
          autenticacion.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .anyMatch(authority -> authority.equals(PREFIJO_ROL + candidato.name()));
      if (loTiene) {
        return Optional.of(candidato);
      }
    }
    return Optional.empty();
  }
}
