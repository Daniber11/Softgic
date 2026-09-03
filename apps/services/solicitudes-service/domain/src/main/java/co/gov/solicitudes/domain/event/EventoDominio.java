package co.gov.solicitudes.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Hecho de negocio ya ocurrido.
 *
 * <p>La jerarquia es sellada: el compilador conoce el conjunto completo de eventos, de modo que un
 * switch sobre ellos falla al compilar si manana se agrega uno nuevo y alguien olvida tratarlo.
 * Esa es la ventaja concreta sobre una interfaz abierta.
 *
 * <p>El dominio emite el hecho desnudo. No conoce el sobre con eventId, version ni correlationId:
 * de eso se encarga el adaptador de salida al escribir en el outbox. Si el dominio conociera el
 * sobre, conoceria el transporte.
 */
public sealed interface EventoDominio
    permits SolicitudRegistrada,
        SolicitudTomada,
        SolicitudResuelta,
        SolicitudDevuelta,
        SolicitudCerrada {

  UUID agregadoId();

  Instant ocurridoEn();

  /** Nombre estable del evento. Viaja en el sobre y es parte del contrato con el consumidor. */
  String tipo();

  /** Clave de enrutamiento en el exchange topic. */
  String routingKey();
}
