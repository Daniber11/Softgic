package co.gov.indicadores.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Sobre comun de los eventos de dominio, tal como lo escribe el productor.
 *
 * <p>Es una copia del contrato, no una dependencia compartida por codigo. Un modulo comun entre
 * los dos servicios los ataria a desplegarse juntos, que es precisamente lo que la mensajeria
 * asincrona evita. El precio de duplicar veinte lineas es muy inferior al de acoplar dos
 * microservicios.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} es la mitad del contrato de evolucion:
 * el productor puede agregar campos opcionales sin romper a este consumidor. La otra mitad es la
 * regla de que un cambio rompiente incrementa la version del evento.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SobreEvento(
    UUID eventId,
    String type,
    int version,
    Instant occurredAt,
    UUID aggregateId,
    String aggregateType,
    String correlationId,
    String producer,
    Datos data) {

  /**
   * Carga util del evento.
   *
   * <p>Se declaran solo los campos que la proyeccion usa. Los identificadores de persona
   * —solicitanteId, analistaId, supervisorId— viajan en el mensaje pero NO se declaran aqui: lo
   * que no se deserializa no puede persistirse por descuido. Es minimizacion de datos aplicada en
   * el tipo, no en una revision de codigo (ADR-005).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Datos(String codigo, UUID categoriaId) {}
}
