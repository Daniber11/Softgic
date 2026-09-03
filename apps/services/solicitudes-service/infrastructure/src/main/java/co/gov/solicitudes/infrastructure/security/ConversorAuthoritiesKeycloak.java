package co.gov.solicitudes.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Traduce los roles de realm de Keycloak a authorities de Spring Security.
 *
 * <p><b>Por que este codigo es tan defensivo.</b> Se verifico contra el Keycloak real del stack:
 * un usuario sin ningun rol de realm no recibe un {@code realm_access} con lista vacia, sino un
 * token <b>sin la claim en absoluto</b>. Un conversor que hiciera
 * {@code jwt.getClaimAsMap("realm_access").get("roles")} lanzaria NullPointerException, Spring lo
 * traduciria a 500 y el escenario A3 —usuario sin rol intenta cerrar— devolveria un error de
 * servidor en lugar del 403 que el reto exige. Cada rama nula de aqui corresponde a una forma real
 * en que el token puede llegar.
 *
 * <p>Se antepone el prefijo ROLE_ porque es lo que espera {@code hasRole(...)} en las expresiones
 * de {@code @PreAuthorize}. Sin el, la anotacion nunca coincidiria y la autorizacion fallaria en
 * silencio permitiendo o negando de forma equivocada.
 */
public class ConversorAuthoritiesKeycloak
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String CLAIM_REALM_ACCESS = "realm_access";
  private static final String CLAVE_ROLES = "roles";
  private static final String PREFIJO_ROL = "ROLE_";

  /** Solo se aceptan los roles del sistema; cualquier otro rol de Keycloak se ignora. */
  private static final Set<String> ROLES_CONOCIDOS = Set.of("SOLICITANTE", "ANALISTA", "SUPERVISOR");

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    return new JwtAuthenticationToken(jwt, extraerAuthorities(jwt), jwt.getSubject());
  }

  private Collection<GrantedAuthority> extraerAuthorities(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
    if (realmAccess == null) {
      // Caso real y verificado: usuario sin roles de realm.
      return List.of();
    }

    Object roles = realmAccess.get(CLAVE_ROLES);
    if (!(roles instanceof Collection<?> listaDeRoles)) {
      return List.of();
    }

    return listaDeRoles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(ROLES_CONOCIDOS::contains)
        .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIJO_ROL + rol))
        .toList();
  }
}
