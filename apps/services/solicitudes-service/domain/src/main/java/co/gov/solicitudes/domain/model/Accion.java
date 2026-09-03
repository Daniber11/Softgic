package co.gov.solicitudes.domain.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Tabla de transiciones del sistema.
 *
 * <p>Cada accion declara las tres cosas que la definen: el rol que puede ejecutarla, el estado del
 * que parte y el estado al que lleva. Esta enumeracion es la <b>unica</b> fuente de verdad de la
 * maquina de estados; agregar una transicion significa agregar una constante aqui y nada mas.
 *
 * <p><b>Por que la accion declara su estado de origen y no basta con el destino.</b> Desde RESUELTA
 * se puede llegar a EN_ATENCION, pero solo mediante DEVOLVER. Si la regla se expresara unicamente
 * como "que destinos son alcanzables desde este estado", TOMAR sobre una solicitud RESUELTA se
 * aceptaria por error, porque su destino EN_ATENCION si es alcanzable. El par (origen, accion) es
 * lo que hay que validar, no el destino suelto.
 */
public enum Accion {
  TOMAR(Rol.ANALISTA, EstadoSolicitud.REGISTRADA, EstadoSolicitud.EN_ATENCION),
  RESOLVER(Rol.ANALISTA, EstadoSolicitud.EN_ATENCION, EstadoSolicitud.RESUELTA),
  DEVOLVER(Rol.SUPERVISOR, EstadoSolicitud.RESUELTA, EstadoSolicitud.EN_ATENCION),
  CERRAR(Rol.SUPERVISOR, EstadoSolicitud.RESUELTA, EstadoSolicitud.CERRADA);

  private final Rol rolRequerido;
  private final EstadoSolicitud origen;
  private final EstadoSolicitud destino;

  Accion(Rol rolRequerido, EstadoSolicitud origen, EstadoSolicitud destino) {
    this.rolRequerido = rolRequerido;
    this.origen = origen;
    this.destino = destino;
  }

  public Rol rolRequerido() {
    return rolRequerido;
  }

  public EstadoSolicitud origen() {
    return origen;
  }

  public EstadoSolicitud destino() {
    return destino;
  }

  public boolean aplicaDesde(EstadoSolicitud estadoActual) {
    return origen == estadoActual;
  }

  /**
   * Acciones disponibles desde un estado. Se calcula desde la misma tabla, de modo que no puede
   * quedar desincronizada con las transiciones reales.
   */
  public static Set<Accion> disponiblesDesde(EstadoSolicitud estado) {
    return Collections.unmodifiableSet(POR_ESTADO.getOrDefault(estado, EnumSet.noneOf(Accion.class)));
  }

  private static final Map<EstadoSolicitud, EnumSet<Accion>> POR_ESTADO = construirIndicePorEstado();

  private static Map<EstadoSolicitud, EnumSet<Accion>> construirIndicePorEstado() {
    Map<EstadoSolicitud, EnumSet<Accion>> indice = new EnumMap<>(EstadoSolicitud.class);
    for (EstadoSolicitud estado : EstadoSolicitud.values()) {
      indice.put(estado, EnumSet.noneOf(Accion.class));
    }
    Arrays.stream(values()).forEach(accion -> indice.get(accion.origen).add(accion));
    return indice;
  }
}
