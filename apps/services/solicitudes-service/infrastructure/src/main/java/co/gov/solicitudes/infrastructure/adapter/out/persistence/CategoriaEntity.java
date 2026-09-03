package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Entrada del catalogo de categorias. */
@Entity
@Table(name = "categoria")
public class CategoriaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "codigo", nullable = false, length = 40)
  private String codigo;

  @Column(name = "nombre", nullable = false, length = 120)
  private String nombre;

  @Column(name = "activa", nullable = false)
  private boolean activa;

  protected CategoriaEntity() {
    // Exigido por JPA.
  }

  public UUID getId() {
    return id;
  }

  public String getCodigo() {
    return codigo;
  }

  public String getNombre() {
    return nombre;
  }

  public boolean isActiva() {
    return activa;
  }
}
