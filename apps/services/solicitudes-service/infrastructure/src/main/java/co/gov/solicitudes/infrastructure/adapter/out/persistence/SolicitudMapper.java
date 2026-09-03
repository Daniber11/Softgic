package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.CambioEstado;
import co.gov.solicitudes.domain.model.Categoria;
import co.gov.solicitudes.domain.model.CategoriaId;
import co.gov.solicitudes.domain.model.CodigoSolicitud;
import co.gov.solicitudes.domain.model.EstadoSolicitud;
import co.gov.solicitudes.domain.model.Observacion;
import co.gov.solicitudes.domain.model.Prioridad;
import co.gov.solicitudes.domain.model.Rol;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduccion entre el modelo relacional y el de dominio.
 *
 * <p>Es la capa anticorrupcion. Existe porque los dos modelos cambian por razones distintas: el
 * relacional cuando cambia el esquema, el de dominio cuando cambian las reglas. Sin este mapper,
 * un renombrado de columna se propagaria hasta el agregado.
 *
 * <p>Los enums viajan a la base como texto y no por su ordinal. Guardar el ordinal ata el
 * significado de los datos al orden de declaracion en Java: insertar un estado nuevo en medio del
 * enum reinterpretaria en silencio todas las filas historicas.
 */
@Component
public class SolicitudMapper {

  /** De dominio a entidad, reutilizando la entidad existente para no romper el bloqueo optimista. */
  public SolicitudEntity aEntidad(Solicitud solicitud, SolicitudEntity destino) {
    SolicitudEntity entidad = destino != null ? destino : new SolicitudEntity();

    entidad.setId(solicitud.id().valor());
    entidad.setCodigo(solicitud.codigo().valor());
    entidad.setAsunto(solicitud.asunto());
    entidad.setDescripcion(solicitud.descripcion());
    entidad.setCategoriaId(solicitud.categoriaId().valor());
    entidad.setPrioridad(solicitud.prioridad().name());
    entidad.setEstado(solicitud.estado().name());
    entidad.setSolicitanteId(solicitud.solicitanteId());
    entidad.setAnalistaId(solicitud.analista().orElse(null));
    entidad.setCreadaEn(solicitud.creadaEn());
    entidad.setActualizadaEn(solicitud.actualizadaEn());

    sincronizarHistorial(solicitud, entidad);
    sincronizarObservaciones(solicitud, entidad);

    return entidad;
  }

  /**
   * Agrega al historial persistido lo que el agregado tenga de mas.
   *
   * <p>Se anaden solo las filas nuevas en lugar de vaciar y reconstruir la coleccion. Con
   * orphanRemoval activo, limpiar la lista borraria el historial completo en cada guardado y lo
   * reinsertaria: seria correcto de cara al usuario y desastroso para la trazabilidad, porque los
   * identificadores de las entradas cambiarian en cada transicion.
   */
  private void sincronizarHistorial(Solicitud solicitud, SolicitudEntity entidad) {
    List<CambioEstado> historialDominio = solicitud.historial();
    for (int i = entidad.getHistorial().size(); i < historialDominio.size(); i++) {
      entidad.getHistorial().add(aEntidad(historialDominio.get(i)));
    }
  }

  private void sincronizarObservaciones(Solicitud solicitud, SolicitudEntity entidad) {
    List<Observacion> observacionesDominio = solicitud.observaciones();
    for (int i = entidad.getObservaciones().size(); i < observacionesDominio.size(); i++) {
      entidad.getObservaciones().add(aEntidad(observacionesDominio.get(i)));
    }
  }

  private CambioEstadoEntity aEntidad(CambioEstado cambio) {
    CambioEstadoEntity entidad = new CambioEstadoEntity();
    entidad.setId(cambio.id());
    entidad.setEstadoOrigen(cambio.origen().map(EstadoSolicitud::name).orElse(null));
    entidad.setEstadoDestino(cambio.destino().name());
    entidad.setActorId(cambio.actor().id());
    entidad.setActorRol(cambio.actor().rol().name());
    entidad.setMotivo(cambio.motivo().orElse(null));
    entidad.setOcurridoEn(cambio.ocurridoEn());
    return entidad;
  }

  private ObservacionEntity aEntidad(Observacion observacion) {
    ObservacionEntity entidad = new ObservacionEntity();
    entidad.setId(observacion.id());
    entidad.setTexto(observacion.texto());
    entidad.setActorId(observacion.autor().id());
    entidad.setActorRol(observacion.autor().rol().name());
    entidad.setOcurridoEn(observacion.ocurridoEn());
    return entidad;
  }

  /** De entidad a dominio. Usa rehidratar, que no valida reglas ni emite eventos. */
  public Solicitud aDominio(SolicitudEntity entidad) {
    return Solicitud.rehidratar(
        new SolicitudId(entidad.getId()),
        new CodigoSolicitud(entidad.getCodigo()),
        entidad.getAsunto(),
        entidad.getDescripcion(),
        new CategoriaId(entidad.getCategoriaId()),
        Prioridad.valueOf(entidad.getPrioridad()),
        EstadoSolicitud.valueOf(entidad.getEstado()),
        entidad.getSolicitanteId(),
        entidad.getAnalistaId(),
        entidad.getCreadaEn(),
        entidad.getActualizadaEn(),
        entidad.getHistorial().stream().map(this::aDominio).toList(),
        entidad.getObservaciones().stream().map(this::aDominio).toList());
  }

  private CambioEstado aDominio(CambioEstadoEntity entidad) {
    return new CambioEstado(
        entidad.getId(),
        entidad.getEstadoOrigen() == null
            ? null
            : EstadoSolicitud.valueOf(entidad.getEstadoOrigen()),
        EstadoSolicitud.valueOf(entidad.getEstadoDestino()),
        new Actor(entidad.getActorId(), Rol.valueOf(entidad.getActorRol())),
        entidad.getMotivo(),
        entidad.getOcurridoEn());
  }

  /**
   * Version para la bandeja: no toca historial ni observaciones.
   *
   * <p>No es una micro-optimizacion. El listado devuelve hasta cien filas y el mapeo completo
   * dispararia dos consultas adicionales por cada una, el clasico N+1: ciento una consultas para
   * pintar una tabla que ademas no muestra esos datos. El resumen que viaja al cliente tampoco los
   * incluye, de modo que cargarlos seria trabajo puro sin destinatario.
   */
  public Solicitud aDominioResumen(SolicitudEntity entidad) {
    return Solicitud.rehidratar(
        new SolicitudId(entidad.getId()),
        new CodigoSolicitud(entidad.getCodigo()),
        entidad.getAsunto(),
        entidad.getDescripcion(),
        new CategoriaId(entidad.getCategoriaId()),
        Prioridad.valueOf(entidad.getPrioridad()),
        EstadoSolicitud.valueOf(entidad.getEstado()),
        entidad.getSolicitanteId(),
        entidad.getAnalistaId(),
        entidad.getCreadaEn(),
        entidad.getActualizadaEn(),
        List.of(),
        List.of());
  }

  private Observacion aDominio(ObservacionEntity entidad) {
    return new Observacion(
        entidad.getId(),
        entidad.getTexto(),
        new Actor(entidad.getActorId(), Rol.valueOf(entidad.getActorRol())),
        entidad.getOcurridoEn());
  }

  public Categoria aDominio(CategoriaEntity entidad) {
    return new Categoria(
        new CategoriaId(entidad.getId()),
        entidad.getCodigo(),
        entidad.getNombre(),
        entidad.isActiva());
  }
}
