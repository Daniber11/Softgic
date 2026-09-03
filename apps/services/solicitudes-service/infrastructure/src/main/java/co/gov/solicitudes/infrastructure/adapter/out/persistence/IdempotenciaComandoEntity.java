package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro de una peticion ya atendida, indexado por la cabecera {@code Idempotency-Key}.
 *
 * <p>Guarda el hash del cuerpo para distinguir dos situaciones que parecen la misma: un reintento
 * legitimo del cliente —misma llave, mismo cuerpo, al que hay que devolver la respuesta original
 * sin volver a crear nada— y una colision de llaves —misma llave, cuerpo distinto, que es un error
 * del cliente y se responde 409.
 */
@Entity
@Table(name = "idempotencia_comando")
public class IdempotenciaComandoEntity {

  @Id
  @Column(name = "llave", nullable = false, length = 120)
  private String llave;

  @Column(name = "hash_cuerpo", nullable = false, length = 64)
  private String hashCuerpo;

  @Column(name = "estado_http", nullable = false)
  private int estadoHttp;

  @Column(name = "respuesta", nullable = false)
  private String respuesta;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "creada_en", nullable = false)
  private Instant creadaEn;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "expira_en", nullable = false)
  private Instant expiraEn;

  protected IdempotenciaComandoEntity() {
    // Exigido por JPA.
  }

  public static IdempotenciaComandoEntity registrar(
      String llave,
      String hashCuerpo,
      int estadoHttp,
      String respuesta,
      Instant creadaEn,
      Instant expiraEn) {
    IdempotenciaComandoEntity fila = new IdempotenciaComandoEntity();
    fila.llave = llave;
    fila.hashCuerpo = hashCuerpo;
    fila.estadoHttp = estadoHttp;
    fila.respuesta = respuesta;
    fila.creadaEn = creadaEn;
    fila.expiraEn = expiraEn;
    return fila;
  }

  public String getLlave() {
    return llave;
  }

  public String getHashCuerpo() {
    return hashCuerpo;
  }

  public int getEstadoHttp() {
    return estadoHttp;
  }

  public String getRespuesta() {
    return respuesta;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }
}
