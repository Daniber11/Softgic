package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** Comentario adjunto a una solicitud. */
@Entity
@Table(name = "observacion")
public class ObservacionEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "texto", nullable = false, length = 1000)
  private String texto;

  @Column(name = "actor_id", nullable = false, length = 80)
  private String actorId;

  @Column(name = "actor_rol", nullable = false, length = 20)
  private String actorRol;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  protected ObservacionEntity() {
    // Exigido por JPA.
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTexto() {
    return texto;
  }

  public void setTexto(String texto) {
    this.texto = texto;
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

  public Instant getOcurridoEn() {
    return ocurridoEn;
  }

  public void setOcurridoEn(Instant ocurridoEn) {
    this.ocurridoEn = ocurridoEn;
  }
}
