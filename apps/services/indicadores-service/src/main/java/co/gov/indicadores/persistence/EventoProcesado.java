package co.gov.indicadores.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Marca de que un evento ya fue proyectado.
 *
 * <p>La clave primaria es el eventId que asigno el productor. Esa unica restriccion es todo el
 * mecanismo de idempotencia: no hay comprobacion previa, no hay bloqueo, no hay ventana de
 * carrera. Si el evento ya llego, el INSERT falla y la transaccion completa se revierte.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "tipo", nullable = false, length = 60)
  private String tipo;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "procesado_en", nullable = false)
  private Instant procesadoEn;

  protected EventoProcesado() {
    // Exigido por JPA.
  }

  public static EventoProcesado de(UUID eventId, String tipo, Instant procesadoEn) {
    EventoProcesado registro = new EventoProcesado();
    registro.eventId = eventId;
    registro.tipo = tipo;
    registro.procesadoEn = procesadoEn;
    return registro;
  }
}
