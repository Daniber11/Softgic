package co.gov.indicadores.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Dimension de categoria.
 *
 * <p>Replica el catalogo del servicio operacional. Duplicar la dimension junto al hecho es lo
 * normal en un esquema en estrella: permite que las consultas analiticas se resuelvan en una sola
 * base, sin llamadas sincronas al otro servicio que lo acoplarian y lo harian caer con el.
 */
@Entity
@Table(name = "dim_categoria")
public class DimCategoria {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "categoria_key")
  private Integer categoriaKey;

  @Column(name = "categoria_id", nullable = false)
  private UUID categoriaId;

  @Column(name = "codigo", nullable = false, length = 40)
  private String codigo;

  @Column(name = "nombre", nullable = false, length = 120)
  private String nombre;

  protected DimCategoria() {
    // Exigido por JPA.
  }

  public Integer getCategoriaKey() {
    return categoriaKey;
  }

  public String getCodigo() {
    return codigo;
  }
}
