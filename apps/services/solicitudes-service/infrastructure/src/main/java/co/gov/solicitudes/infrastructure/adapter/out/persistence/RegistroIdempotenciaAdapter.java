package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import co.gov.solicitudes.infrastructure.idempotencia.RegistroIdempotencia;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistencia de las llaves de idempotencia. */
@Component
public class RegistroIdempotenciaAdapter implements RegistroIdempotencia {

  /**
   * Ventana de validez de una llave.
   *
   * <p>Veinticuatro horas cubre de sobra el reintento de un cliente ante un fallo de red, que es
   * el caso que la idempotencia resuelve, sin que la tabla crezca indefinidamente.
   */
  private static final Duration VIGENCIA = Duration.ofHours(24);

  private final IdempotenciaJpaRepository repositorio;
  private final Clock clock;

  public RegistroIdempotenciaAdapter(IdempotenciaJpaRepository repositorio, Clock clock) {
    this.repositorio = repositorio;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RespuestaRegistrada> buscar(String llave) {
    return repositorio
        .findById(llave)
        // Una llave vencida se trata como inexistente: la peticion se procesa de
        // nuevo en lugar de devolver una respuesta de hace dias.
        .filter(fila -> fila.getExpiraEn().isAfter(Instant.now(clock)))
        .map(
            fila ->
                new RespuestaRegistrada(
                    fila.getHashCuerpo(), fila.getEstadoHttp(), fila.getRespuesta()));
  }

  @Override
  @Transactional
  public void registrar(String llave, String hashCuerpo, int estadoHttp, String respuesta) {
    Instant ahora = Instant.now(clock);
    repositorio.save(
        IdempotenciaComandoEntity.registrar(
            llave, hashCuerpo, estadoHttp, respuesta, ahora, ahora.plus(VIGENCIA)));
  }
}
