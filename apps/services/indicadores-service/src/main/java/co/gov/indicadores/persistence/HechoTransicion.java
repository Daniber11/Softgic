package co.gov.indicadores.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de la tabla de hechos: una transicion de estado ocurrida. */
@Entity
@Table(name = "hecho_transicion")
public class HechoTransicion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "solicitud_id", nullable = false)
  private UUID solicitudId;

  @Column(name = "solicitud_codigo", nullable = false, length = 20)
  private String solicitudCodigo;

  @Column(name = "fecha_key", nullable = false)
  private int fechaKey;

  @Column(name = "categoria_key", nullable = false)
  private int categoriaKey;

  @Column(name = "estado_origen_key", nullable = false)
  private int estadoOrigenKey;

  @Column(name = "estado_destino_key", nullable = false)
  private int estadoDestinoKey;

  @Column(name = "rol_key", nullable = false)
  private int rolKey;

  /** Nulo en la fila de registro: no hay transicion previa desde la cual medir. */
  @Column(name = "duracion_minutos")
  private Integer duracionMinutos;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  protected HechoTransicion() {
    // Exigido por JPA.
  }

  public static HechoTransicion de(
      UUID solicitudId,
      String solicitudCodigo,
      int fechaKey,
      int categoriaKey,
      int estadoOrigenKey,
      int estadoDestinoKey,
      int rolKey,
      Integer duracionMinutos,
      Instant ocurridoEn) {
    HechoTransicion hecho = new HechoTransicion();
    hecho.solicitudId = solicitudId;
    hecho.solicitudCodigo = solicitudCodigo;
    hecho.fechaKey = fechaKey;
    hecho.categoriaKey = categoriaKey;
    hecho.estadoOrigenKey = estadoOrigenKey;
    hecho.estadoDestinoKey = estadoDestinoKey;
    hecho.rolKey = rolKey;
    hecho.duracionMinutos = duracionMinutos;
    hecho.ocurridoEn = ocurridoEn;
    return hecho;
  }

  public Long getId() {
    return id;
  }

  public Instant getOcurridoEn() {
    return ocurridoEn;
  }
}
