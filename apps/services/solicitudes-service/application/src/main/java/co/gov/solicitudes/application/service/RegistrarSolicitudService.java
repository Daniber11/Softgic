package co.gov.solicitudes.application.service;

import co.gov.solicitudes.application.command.RegistrarSolicitudCommand;
import co.gov.solicitudes.application.port.in.RegistrarSolicitudUseCase;
import co.gov.solicitudes.application.port.out.CategoriaRepositoryPort;
import co.gov.solicitudes.application.port.out.EventoPublicadorPort;
import co.gov.solicitudes.application.port.out.GeneradorCodigoPort;
import co.gov.solicitudes.application.port.out.RelojPort;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.domain.exception.CategoriaInactivaException;
import co.gov.solicitudes.domain.model.Categoria;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;
import java.time.Instant;
import java.util.Objects;

/**
 * Escenario A1: registro de una solicitud valida.
 *
 * <p>Clase plana, sin una sola anotacion de Spring. Es {@code BeanConfiguration} en el modulo de
 * arranque quien la instancia y le inyecta los puertos, de modo que el grafo de dependencias del
 * sistema se lee en un unico archivo.
 *
 * <p>La transaccionalidad se aplica desde fuera, en el adaptador, para que este codigo no tenga
 * que conocer {@code @Transactional}. La garantia que si expresa aqui es el orden: primero se
 * guarda el agregado, despues se entregan sus eventos; ambas cosas ocurren dentro de la misma
 * transaccion abierta por el adaptador.
 */
public final class RegistrarSolicitudService implements RegistrarSolicitudUseCase {

  private final SolicitudRepositoryPort solicitudes;
  private final CategoriaRepositoryPort categorias;
  private final EventoPublicadorPort eventos;
  private final GeneradorCodigoPort generadorCodigo;
  private final RelojPort reloj;

  public RegistrarSolicitudService(
      SolicitudRepositoryPort solicitudes,
      CategoriaRepositoryPort categorias,
      EventoPublicadorPort eventos,
      GeneradorCodigoPort generadorCodigo,
      RelojPort reloj) {
    this.solicitudes = Objects.requireNonNull(solicitudes);
    this.categorias = Objects.requireNonNull(categorias);
    this.eventos = Objects.requireNonNull(eventos);
    this.generadorCodigo = Objects.requireNonNull(generadorCodigo);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public Solicitud registrar(RegistrarSolicitudCommand comando) {
    exigirCategoriaActiva(comando);

    Instant ahora = reloj.ahora();
    Solicitud solicitud =
        Solicitud.registrar(
            SolicitudId.nuevo(),
            generadorCodigo.siguiente(),
            comando.asunto(),
            comando.descripcion(),
            comando.categoriaId(),
            comando.prioridad(),
            comando.solicitante(),
            ahora);

    Solicitud guardada = solicitudes.guardar(solicitud);
    eventos.publicar(solicitud.drenarEventos());
    return guardada;
  }

  /**
   * La categoria debe existir y estar activa.
   *
   * <p>Es una regla de negocio, pero no puede vivir en el agregado: Solicitud solo conoce el
   * identificador de la categoria, no el catalogo. Comprobarla aqui es lo que corresponde a un
   * caso de uso, que si puede consultar otros agregados.
   */
  private void exigirCategoriaActiva(RegistrarSolicitudCommand comando) {
    Categoria categoria =
        categorias
            .buscarPorId(comando.categoriaId())
            .orElseThrow(
                () ->
                    new CategoriaInactivaException(
                        "La categoria indicada no existe en el catalogo."));

    if (!categoria.activa()) {
      throw new CategoriaInactivaException(
          "La categoria %s fue retirada del catalogo y no admite solicitudes nuevas."
              .formatted(categoria.codigo()));
    }
  }
}
