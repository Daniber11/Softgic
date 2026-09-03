package co.gov.indicadores.web;

import co.gov.indicadores.service.ConsultaIndicadoresService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas del modelo de lectura.
 *
 * <p>Solo lectura: este servicio no acepta comandos. La unica forma de que sus datos cambien es
 * que llegue un evento, lo que hace imposible que la proyeccion y el modelo operacional diverjan
 * por una escritura directa.
 */
@RestController
@RequestMapping("/api/v1/indicadores")
@Tag(name = "Indicadores", description = "Modelo de lectura alimentado por eventos")
public class IndicadorController {

  private final ConsultaIndicadoresService consultas;

  public IndicadorController(ConsultaIndicadoresService consultas) {
    this.consultas = consultas;
  }

  @GetMapping("/resumen")
  @PreAuthorize("hasAnyRole('ANALISTA','SUPERVISOR')")
  @Operation(summary = "Conteos por estado actual y por categoria, mas el tiempo medio de atencion")
  public ResumenResponse resumen() {
    return new ResumenResponse(
        consultas.solicitudesPorEstado(),
        consultas.solicitudesPorCategoria(),
        consultas.promedioMinutosHastaResolucion());
  }

  @GetMapping("/tendencia")
  @PreAuthorize("hasAnyRole('ANALISTA','SUPERVISOR')")
  @Operation(summary = "Solicitudes registradas por dia, en formato AAAA-MM-DD")
  public TendenciaResponse tendencia(
      @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
    return new TendenciaResponse(consultas.tendenciaDiaria(desde, hasta));
  }

  /** El promedio se expone junto al resumen para que la vista no encadene dos llamadas. */
  public record ResumenResponse(
      Map<String, Long> porEstado,
      Map<String, Long> porCategoria,
      long promedioMinutosHastaResolucion) {}

  public record TendenciaResponse(Map<String, Long> porDia) {}
}
