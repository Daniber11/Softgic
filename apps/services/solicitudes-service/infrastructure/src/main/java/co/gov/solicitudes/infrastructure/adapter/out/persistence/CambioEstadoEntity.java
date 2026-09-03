package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** Fila del historial de transiciones. estado_origen es nulo en el registro inicial. */
@Entity
@Table(name = "cambio_estado")
public class CambioEstadoEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "estado_origen", length = 20)
  private String estadoOrigen;

  @Column(name = "estado_destino", nullable = false, length = 20)
  private String estadoDestino;

  @Column(name = "actor_id", nullable = false, length = 80)
  private String actorId;

  @Column(name = "actor_rol", nullable = false, length = 20)
  private String actorRol;

  @Column(name = "motivo", length = 500)
  private String motivo;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  protected CambioEstadoEntity() {
    // Exigido por JPA.
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getEstadoOrigen() {
    return estadoOrigen;
  }

  public void setEstadoOrigen(String estadoOrigen) {
    this.estadoOrigen = estadoOrigen;
  }

  public String getEstadoDestino() {
    return estadoDestino;
  }

  public void setEstadoDestino(String estadoDestino) {
    this.estadoDestino = estadoDestino;
  }

  public String getActorId() {
    return actorId;
  }

  public void setActorId(String actorId) {
    this.actorId = actorId;
  }

  public String getActorRol() {
    return actorRol;
  }

  public void setActorRol(String actorRol) {
    this.actorRol = actorRol;
  }

  public String getMotivo() {
    return motivo;
  }

  public void setMotivo(String motivo) {
    this.motivo = motivo;
  }

  public Instant getOcurridoEn() {
    return ocurridoEn;
  }

  public void setOcurridoEn(Instant ocurridoEn) {
    this.ocurridoEn = ocurridoEn;
  }
}
