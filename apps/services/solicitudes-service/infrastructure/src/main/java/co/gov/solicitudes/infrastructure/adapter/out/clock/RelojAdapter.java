package co.gov.solicitudes.infrastructure.adapter.out.clock;

import co.gov.solicitudes.application.port.out.RelojPort;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Reloj del sistema.
 *
 * <p>Envuelve java.time.Clock en lugar de llamar a Instant.now() para que en pruebas de
 * integracion se pueda inyectar un Clock fijo sin tocar el codigo de produccion.
 */
@Component
public class RelojAdapter implements RelojPort {

  private final Clock clock;

  public RelojAdapter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Instant ahora() {
    return Instant.now(clock);
  }
}
