package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representacion relacional de la solicitud.
 *
 * <p>Es un tipo distinto del agregado de dominio a proposito. Tienen razones de cambio distintas:
 * esta cambia cuando cambia el esquema, aquel cuando cambian las reglas de negocio. Anotar el
 * agregado con {@code @Entity} los ataria, y ademas obligaria a que el dominio dependiera de JPA,
 * cosa que la prueba de ArchUnit impide.
 *
 * <p>Sin logica: solo campos y accesores. Toda la traduccion vive en {@link SolicitudMapper}.
 */
@Entity
@Table(name = "solicitud")
public class SolicitudEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "codigo", nullable = false, length = 20)
  private String codigo;

  @Column(name = "asunto", nullable = false, length = 200)
  private String asunto;

  @Column(name = "descripcion", nullable = false, length = 2000)
  private String descripcion;

  @Column(name = "categoria_id", nullable = false)
  private UUID categoriaId;

  @Column(name = "prioridad", nullable = false, length = 10)
  private String prioridad;

  @Column(name = "estado", nullable = false, length = 20)
  private String estado;

  @Column(name = "solicitante_id", nullable = false, length = 80)
  private String solicitanteId;

  @Column(name = "analista_id", length = 80)
  private String analistaId;

  /**
   * Las marcas de tiempo se almacenan como DATETIME2 y no como DATETIMEOFFSET.
   *
   * <p>Hibernate 6 mapea Instant a TIMESTAMP_UTC por omision, que en SQL Server es
   * DATETIMEOFFSET. Aqui se fuerza TIMESTAMP porque todo instante del sistema es UTC
   * por construccion: el reloj se inyecta como RelojPort y devuelve Instant, y la
   * conexion declara hibernate.jdbc.time_zone=UTC. Una columna con desplazamiento
   * guardaria siempre +00:00, ocupando mas espacio sin aportar informacion.
   *
   * <p>El desajuste lo detecto ddl-auto=validate al arrancar, que es exactamente para
   * lo que esta puesto: sin el, la discrepancia habria aparecido como un error de
   * conversion en tiempo de ejecucion.
   */
  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "creada_en", nullable = false)
  private Instant creadaEn;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "actualizada_en", nullable = false)
  private Instant actualizadaEn;

  /**
   * Bloqueo optimista. Es el UNICO mecanismo de control de concurrencia del sistema y el que
   * resuelve el escenario A2: la segunda escritura simultanea afecta cero filas, Hibernate lanza
   * OptimisticLockingFailureException y el borde REST responde 409.
   */
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "solicitud_id", nullable = false)
  @OrderBy("ocurridoEn ASC")
  private List<CambioEstadoEntity> historial = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "solicitud_id", nullable = false)
  @OrderBy("ocurridoEn ASC")
  private List<ObservacionEntity> observaciones = new ArrayList<>();

  protected SolicitudEntity() {
    // Exigido por JPA.
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getCodigo() {
    return codigo;
  }

  public void setCodigo(String codigo) {
    this.codigo = codigo;
  }

  public String getAsunto() {
    return asunto;
  }

  public void setAsunto(String asunto) {
    this.asunto = asunto;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public UUID getCategoriaId() {
    return categoriaId;
  }

  public void setCategoriaId(UUID categoriaId) {
    this.categoriaId = categoriaId;
  }

  public String getPrioridad() {
    return prioridad;
  }

  public void setPrioridad(String prioridad) {
    this.prioridad = prioridad;
  }

  public String getEstado() {
    return estado;
  }

  public void setEstado(String estado) {
    this.estado = estado;
  }

  public String getSolicitanteId() {
    return solicitanteId;
  }

  public void setSolicitanteId(String solicitanteId) {
    this.solicitanteId = solicitanteId;
  }

  public String getAnalistaId() {
    return analistaId;
  }

  public void setAnalistaId(String analistaId) {
    this.analistaId = analistaId;
  }

  public Instant getCreadaEn() {
    return creadaEn;
  }

  public void setCreadaEn(Instant creadaEn) {
    this.creadaEn = creadaEn;
  }

  public Instant getActualizadaEn() {
    return actualizadaEn;
  }

  public void setActualizadaEn(Instant actualizadaEn) {
    this.actualizadaEn = actualizadaEn;
  }

  public long getVersion() {
    return version;
  }

  public List<CambioEstadoEntity> getHistorial() {
    return historial;
  }

  public List<ObservacionEntity> getObservaciones() {
    return observaciones;
  }
}
