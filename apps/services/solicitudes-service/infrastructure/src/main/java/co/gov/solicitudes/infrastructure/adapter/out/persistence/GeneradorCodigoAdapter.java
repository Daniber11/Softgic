package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import co.gov.solicitudes.application.port.out.GeneradorCodigoPort;
import co.gov.solicitudes.domain.model.CodigoSolicitud;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/**
 * Genera el codigo legible SOL-AAAA-NNNNNN.
 *
 * <p>El consecutivo se calcula desde el maximo del anio en curso. Es suficiente para el alcance de
 * la prueba, pero <b>no es seguro bajo concurrencia alta</b>: dos registros simultaneos podrian
 * calcular el mismo consecutivo. La restriccion UNIQUE sobre solicitud(codigo) impide que se
 * persista el duplicado, de modo que el peor caso es un error, no un codigo repetido.
 *
 * <p>La solucion productiva seria un SEQUENCE de SQL Server por anio. Queda declarado como
 * limitacion conocida en lugar de aparentar una garantia que este codigo no da.
 */
@Component
public class GeneradorCodigoAdapter implements GeneradorCodigoPort {

  private static final String PREFIJO = "SOL";
  private static final int ANCHO_CONSECUTIVO = 6;
  private static final int POSICION_CONSECUTIVO = 9;

  private final SolicitudJpaRepository repositorio;
  private final Clock clock;

  public GeneradorCodigoAdapter(SolicitudJpaRepository repositorio, Clock clock) {
    this.repositorio = repositorio;
    this.clock = clock;
  }

  @Override
  public CodigoSolicitud siguiente() {
    int anio = ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC).getYear();
    String prefijoDelAnio = "%s-%d-".formatted(PREFIJO, anio);

    long siguiente =
        repositorio
            .ultimoCodigoDelAnio(prefijoDelAnio + "%")
            .map(this::extraerConsecutivo)
            .orElse(0L)
        + 1;

    String consecutivoConCeros = String.format("%0" + ANCHO_CONSECUTIVO + "d", siguiente);
    return new CodigoSolicitud(prefijoDelAnio + consecutivoConCeros);
  }

  private long extraerConsecutivo(String codigo) {
    return Long.parseLong(codigo.substring(POSICION_CONSECUTIVO));
  }
}
