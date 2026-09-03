package co.gov.indicadores.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Este servicio tambien es un Resource Server y tambien configura CORS.
 *
 * <p>No hay API Gateway en la solucion: el navegador habla directamente con dos origenes, 8081 y
 * 8082. Olvidar el CORS aqui hace que la vista analitica muera en el navegador con un error que
 * aparenta ser de red y no de configuracion, mientras que por curl todo funciona. Es un fallo caro
 * de diagnosticar y por eso queda anotado.
 *
 * <p>El conversor de authorities es null-safe por la misma razon que en el otro servicio: un
 * usuario sin roles de realm recibe un token <b>sin</b> la claim realm_access, no con una lista
 * vacia. Verificado contra el Keycloak del stack.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  private static final String[] RUTAS_PUBLICAS = {
    "/actuator/health", "/actuator/health/**", "/actuator/info",
    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
  };

  private final String origenesPermitidos;

  public SecurityConfiguration(
      @Value("${indicadores.cors.origenes-permitidos}") String origenesPermitidos) {
    this.origenesPermitidos = origenesPermitidos;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            rutas ->
                rutas
                    .requestMatchers(RUTAS_PUBLICAS)
                    .permitAll()
                    // La vista analitica es para quien atiende y supervisa. Un
                    // solicitante no tiene por que ver la operacion agregada.
                    .requestMatchers("/api/v1/indicadores/**")
                    .hasAnyRole("ANALISTA", "SUPERVISOR")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new ConversorAuthorities())));

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuracion = new CorsConfiguration();
    configuracion.setAllowedOrigins(
        Arrays.stream(origenesPermitidos.split(",")).map(String::strip).toList());
    configuracion.setAllowedMethods(List.of("GET", "OPTIONS"));
    configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
    configuracion.setExposedHeaders(List.of("X-Correlation-Id"));
    configuracion.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
    fuente.registerCorsConfiguration("/api/**", configuracion);
    return fuente;
  }

  /** Traduce realm_access.roles a authorities, tolerando que la claim no exista. */
  static final class ConversorAuthorities implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAVE_ROLES = "roles";
    private static final String PREFIJO_ROL = "ROLE_";
    private static final Set<String> ROLES_CONOCIDOS =
        Set.of("SOLICITANTE", "ANALISTA", "SUPERVISOR");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
      return new JwtAuthenticationToken(jwt, extraer(jwt), jwt.getSubject());
    }

    private Collection<GrantedAuthority> extraer(Jwt jwt) {
      Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
      if (realmAccess == null) {
        return List.of();
      }
      if (!(realmAccess.get(CLAVE_ROLES) instanceof Collection<?> roles)) {
        return List.of();
      }
      return roles.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .filter(ROLES_CONOCIDOS::contains)
          .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIJO_ROL + rol))
          .toList();
    }
  }
}
