package co.gov.solicitudes.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una entrada del historial. Conserva actor, fecha y motivo, que es lo que el reto exige para la
 * trazabilidad.
 *
 * <p><b>Por que no es un record.</b> Dos de sus campos son genuinamente opcionales: el registro
 * inicial no viene de ningun estado previo, y solo DEVOLVER exige motivo. Un record obliga a que
 * el accesor devuelva el tipo del componente, de modo que {@code origen()} tendria que devolver un
 * {@code EstadoSolicitud} nulo y trasladar al consumidor la carga de recordar que puede serlo.
 * Como clase se expone {@link Optional} y la ausencia queda en el tipo, que es donde debe estar.
 */
public final class CambioEstado {

  private final UUID id;
  private final EstadoSolicitud origen;
  private final EstadoSolicitud destino;
  private final Actor actor;
  private final String motivo;
  private final Instant ocurridoEn;

  public CambioEstado(
      UUID id,
      EstadoSolicitud origen,
      EstadoSolicitud destino,
      Actor actor,
      String motivo,
      Instant ocurridoEn) {
    this.id = Objects.requireNonNull(id, "El cambio de estado debe tener identificador.");
    this.origen = origen;
    this.destino = Objects.requireNonNull(destino, "El cambio de estado debe tener destino.");
    this.actor = Objects.requireNonNull(actor, "El cambio de estado debe tener actor.");
    this.motivo = motivo;
    this.ocurridoEn = Objects.requireNonNull(ocurridoEn, "El cambio de estado debe tener fecha.");
  }

  public UUID id() {
    return id;
  }

  /** Vacio en el registro inicial: no hay estado del que se venga. */
  public Optional<EstadoSolicitud> origen() {
    return Optional.ofNullable(origen);
  }

  public EstadoSolicitud destino() {
    return destino;
  }

  public Actor actor() {
    return actor;
  }

  /** Presente solo cuando la accion lo exige, como en DEVOLVER. */
  public Optional<String> motivo() {
    return Optional.ofNullable(motivo);
  }

  public Instant ocurridoEn() {
    return ocurridoEn;
  }

  @Override
  public boolean equals(Object otro) {
    if (this == otro) {
      return true;
    }
    if (!(otro instanceof CambioEstado cambio)) {
      return false;
    }
    return id.equals(cambio.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "CambioEstado[%s -> %s por %s]".formatted(origen, destino, actor.id());
  }
}
