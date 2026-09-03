package co.gov.indicadores.service;

import co.gov.indicadores.persistence.HechoTransicionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas agregadas del modelo de lectura. */
@Service
public class ConsultaIndicadoresService {

  private static final String CLAVE_PROMEDIO = "PROMEDIO_MINUTOS_RESOLUCION";

  private final HechoTransicionRepository hechos;

  public ConsultaIndicadoresService(HechoTransicionRepository hechos) {
    this.hechos = hechos;
  }

  @Transactional(readOnly = true)
  public Map<String, Long> solicitudesPorEstado() {
    return aMapa(hechos.contarPorEstadoActual());
  }

  @Transactional(readOnly = true)
  public Map<String, Long> solicitudesPorCategoria() {
    return aMapa(hechos.contarPorCategoria());
  }

  @Transactional(readOnly = true)
  public Map<String, Long> tendenciaDiaria(String desde, String hasta) {
    return aMapa(hechos.tendenciaDiaria(desde, hasta));
  }

  @Transactional(readOnly = true)
  public long promedioMinutosHastaResolucion() {
    return hechos.promedioMinutosHastaResolucion().stream()
        .filter(fila -> CLAVE_PROMEDIO.equals(fila.getEstado()))
        .map(HechoTransicionRepository.ConteoPorClave::getTotal)
        .findFirst()
        .orElse(0L);
  }

  /** LinkedHashMap para conservar el orden que impuso el ORDER BY de la consulta. */
  private Map<String, Long> aMapa(List<HechoTransicionRepository.ConteoPorClave> filas) {
    return filas.stream()
        .collect(
            Collectors.toMap(
                HechoTransicionRepository.ConteoPorClave::getEstado,
                HechoTransicionRepository.ConteoPorClave::getTotal,
                (a, b) -> a,
                LinkedHashMap::new));
  }
}
