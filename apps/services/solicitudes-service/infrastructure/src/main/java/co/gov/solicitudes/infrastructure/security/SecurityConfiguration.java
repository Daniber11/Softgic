package co.gov.solicitudes.infrastructure.security;

import java.util.Arrays;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Primera capa de la defensa en profundidad: el filtro de seguridad.
 *
 * <p>Las otras dos son {@code @PreAuthorize} en el controlador y la validacion de rol dentro del
 * agregado. Tres capas no es redundancia decorativa: el filtro protege la ruta, la anotacion
 * protege la operacion, y el agregado protege la regla aunque manana el comando llegue por un
 * canal que no sea REST.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  private static final String RUTA_SOLICITUDES = "/api/v1/solicitudes/**";
  private static final String RUTA_CATEGORIAS = "/api/v1/categorias/**";

  private static final String[] RUTAS_PUBLICAS = {
    "/actuator/health", "/actuator/health/**", "/actuator/info",
    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
  };

  private final String origenesPermitidos;
  private final ObjectMapper objectMapper;

  public SecurityConfiguration(
      @Value("${solicitudes.cors.origenes-permitidos}") String origenesPermitidos,
      ObjectMapper objectMapper) {
    this.origenesPermitidos = origenesPermitidos;
    this.objectMapper = objectMapper;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // El API no usa cookies de sesion: el token va en la cabecera Authorization,
        // asi que no hay vector CSRF que proteger y habilitarlo solo romperia los
        // clientes sin aportar seguridad.
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            rutas ->
                rutas
                    .requestMatchers(RUTAS_PUBLICAS)
                    .permitAll()
                    // La bandeja la consultan los tres roles; el caso de uso se
                    // encarga de restringir QUE ve cada uno.
                    .requestMatchers(HttpMethod.GET, RUTA_SOLICITUDES)
                    .hasAnyRole("SOLICITANTE", "ANALISTA", "SUPERVISOR")
                    .requestMatchers(HttpMethod.GET, RUTA_CATEGORIAS)
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes")
                    .hasRole("SOLICITANTE")
                    .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/asignaciones")
                    .hasRole("ANALISTA")
                    .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/transiciones")
                    .hasAnyRole("ANALISTA", "SUPERVISOR")
                    .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/observaciones")
                    .hasAnyRole("ANALISTA", "SUPERVISOR")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(new ConversorAuthoritiesKeycloak())))
        .exceptionHandling(
            manejo ->
                manejo
                    .authenticationEntryPoint(new ManejadorNoAutenticado(objectMapper))
                    .accessDeniedHandler(new ManejadorAccesoDenegado(objectMapper)));

    return http.build();
  }

  /**
   * CORS restringido a una lista explicita de origenes.
   *
   * <p>Nunca "*" con credenciales: el navegador lo rechaza, y aunque no lo hiciera seria abrir el
   * API a cualquier sitio. Los origenes se configuran por variable de entorno para que cambiar de
   * ambiente no exija recompilar.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuracion = new CorsConfiguration();
    configuracion.setAllowedOrigins(Arrays.stream(origenesPermitidos.split(",")).map(String::strip).toList());
    configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuracion.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
    configuracion.setExposedHeaders(List.of("Location", "X-Correlation-Id"));
    configuracion.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
    fuente.registerCorsConfiguration("/api/**", configuracion);
    return fuente;
  }
}
