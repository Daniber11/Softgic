package co.gov.indicadores.consumer;

import java.util.Arrays;
import java.util.Optional;

/**
 * Traduccion de un tipo de evento a la transicion que representa.
 *
 * <p><b>Esto no es logica de negocio, y la distincion importa.</b> El proyector no decide si una
 * transicion es valida: eso ya lo decidio el Servicio de Solicitudes antes de emitir el hecho. Un
 * evento es algo que ya ocurrio. Lo unico que hay aqui es una tabla de correspondencia entre el
 * nombre del hecho y las claves de dimension que le tocan, que es exactamente el trabajo de un
 * proyector.
 *
 * <p>Por eso este servicio no necesita arquitectura hexagonal: no hay invariante que proteger.
 *
 * <p>El rol se deriva del tipo de evento y no del identificador de la persona que aparece en el
 * payload. Ese identificador se descarta a proposito (ADR-005).
 */
public enum TipoDeEvento {

  // El registro no viene de ningun estado previo: se proyecta desde el centinela NINGUNO.
  SOLICITUD_REGISTRADA("SolicitudRegistrada", ClaveEstado.NINGUNO, ClaveEstado.REGISTRADA, ClaveRol.SOLICITANTE),
  SOLICITUD_TOMADA("SolicitudTomada", ClaveEstado.REGISTRADA, ClaveEstado.EN_ATENCION, ClaveRol.ANALISTA),
  SOLICITUD_RESUELTA("SolicitudResuelta", ClaveEstado.EN_ATENCION, ClaveEstado.RESUELTA, ClaveRol.ANALISTA),
  SOLICITUD_DEVUELTA("SolicitudDevuelta", ClaveEstado.RESUELTA, ClaveEstado.EN_ATENCION, ClaveRol.SUPERVISOR),
  SOLICITUD_CERRADA("SolicitudCerrada", ClaveEstado.RESUELTA, ClaveEstado.CERRADA, ClaveRol.SUPERVISOR);

  /** Claves de dim_estado, sembradas por la migracion V1. */
  public static final class ClaveEstado {
    public static final int NINGUNO = 0;
    public static final int REGISTRADA = 1;
    public static final int EN_ATENCION = 2;
    public static final int RESUELTA = 3;
    public static final int CERRADA = 4;

    private ClaveEstado() {}
  }

  /** Claves de dim_rol, sembradas por la migracion V1. */
  public static final class ClaveRol {
    public static final int SOLICITANTE = 1;
    public static final int ANALISTA = 2;
    public static final int SUPERVISOR = 3;

    private ClaveRol() {}
  }

  private final String nombreEnElContrato;
  private final int estadoOrigenKey;
  private final int estadoDestinoKey;
  private final int rolKey;

  TipoDeEvento(String nombreEnElContrato, int estadoOrigenKey, int estadoDestinoKey, int rolKey) {
    this.nombreEnElContrato = nombreEnElContrato;
    this.estadoOrigenKey = estadoOrigenKey;
    this.estadoDestinoKey = estadoDestinoKey;
    this.rolKey = rolKey;
  }

  public int estadoOrigenKey() {
    return estadoOrigenKey;
  }

  public int estadoDestinoKey() {
    return estadoDestinoKey;
  }

  public int rolKey() {
    return rolKey;
  }

  /**
   * Resuelve el tipo declarado en el sobre.
   *
   * <p>Devuelve vacio ante un tipo desconocido en lugar de lanzar. Un evento nuevo emitido por una
   * version mas reciente del productor no debe tumbar al consumidor ni acabar en la DLQ: se
   * confirma y se ignora, que es la unica forma de que ambos servicios puedan desplegarse por
   * separado.
   */
  public static Optional<TipoDeEvento> desde(String nombreEnElContrato) {
    return Arrays.stream(values())
        .filter(tipo -> tipo.nombreEnElContrato.equals(nombreEnElContrato))
        .findFirst();
  }
}
