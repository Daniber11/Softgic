package co.gov.solicitudes.domain.model;

/**
 * Estados por los que pasa una solicitud.
 *
 * <p>Las transiciones permitidas no se declaran aqui sino en {@link Accion}, porque una transicion
 * solo tiene sentido asociada a la accion que la provoca y al rol que puede ejecutarla. Este enum
 * consulta esa tabla en lugar de mantener una copia propia.
 */
public enum EstadoSolicitud {
  REGISTRADA,
  EN_ATENCION,
  RESUELTA,
  CERRADA;

  /** Un estado es final cuando ninguna accion parte de el. */
  public boolean esFinal() {
    return Accion.disponiblesDesde(this).isEmpty();
  }

  public boolean permite(Accion accion) {
    return accion.aplicaDesde(this);
  }
}
