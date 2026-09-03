package co.gov.solicitudes.application.port.out;

import co.gov.solicitudes.domain.event.EventoDominio;
import java.util.List;

/**
 * Salida de los eventos de dominio.
 *
 * <p>El nombre dice "publicador" pero la implementacion escribe en el outbox dentro de la misma
 * transaccion del agregado. Es deliberado: el caso de uso no debe saber si detras hay un broker,
 * una tabla o ambos. Lo que si garantiza el contrato es que un evento entregado aqui se publicara
 * si y solo si la transaccion confirma.
 */
public interface EventoPublicadorPort {
  void publicar(List<EventoDominio> eventos);
}
