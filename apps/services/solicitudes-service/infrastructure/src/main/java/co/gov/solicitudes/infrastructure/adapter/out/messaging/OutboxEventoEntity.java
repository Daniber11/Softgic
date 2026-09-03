package co.gov.solicitudes.infrastructure.adapter.out.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila del outbox transaccional.
 *
 * <p>Se escribe en la MISMA transaccion que el agregado. Esa es toda la idea del patron: publicar
 * antes del commit podria anunciar un hecho que luego se revierte; publicar despues podria perder
 * el evento si el proceso muere en medio. Con la escritura dentro de la transaccion, el hecho y su
 * anuncio son atomicos, y la publicacion al broker se vuelve un problema de reintento, no de
 * consistencia.
 *
 * <p>La tabla es ademas el log durable del sistema: desde ella se puede reproyectar el modelo
 * analitico completo, que es lo que compensa no tener replay nativo como en Kafka.
 */
@Entity
@Table(name = "outbox_evento")
public class OutboxEventoEntity {

  public static final String ESTADO_PENDIENTE = "PENDIENTE";
  public static final String ESTADO_PUBLICADO = "PUBLICADO";
  public static final String ESTADO_FALLIDO = "FALLIDO";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tipo", nullable = false, length = 60)
  private String tipo;

  @Column(name = "version_evento", nullable = false)
  private int versionEvento;

  @Column(name = "agregado_id", nullable = false)
  private UUID agregadoId;

  @Column(name = "agregado_tipo", nullable = false, length = 40)
  private String agregadoTipo;

  @Column(name = "routing_key", nullable = false, length = 80)
  private String routingKey;

  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "correlation_id", nullable = false, length = 60)
  private String correlationId;

  @Column(name = "estado", nullable = false, length = 15)
  private String estado;

  @Column(name = "intentos", nullable = false)
  private int intentos;

  @Column(name = "ultimo_error", length = 1000)
  private String ultimoError;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "publicado_en")
  private Instant publicadoEn;

  protected OutboxEventoEntity() {
    // Exigido por JPA.
  }

  public static OutboxEventoEntity pendiente(
      UUID id,
      String tipo,
      int versionEvento,
      UUID agregadoId,
      String agregadoTipo,
      String routingKey,
      String payload,
      String correlationId,
      Instant ocurridoEn) {
    OutboxEventoEntity fila = new OutboxEventoEntity();
    fila.id = id;
    fila.tipo = tipo;
    fila.versionEvento = versionEvento;
    fila.agregadoId = agregadoId;
    fila.agregadoTipo = agregadoTipo;
    fila.routingKey = routingKey;
    fila.payload = payload;
    fila.correlationId = correlationId;
    fila.estado = ESTADO_PENDIENTE;
    fila.intentos = 0;
    fila.ocurridoEn = ocurridoEn;
    return fila;
  }

  public void marcarPublicado(Instant cuando) {
    this.estado = ESTADO_PUBLICADO;
    this.publicadoEn = cuando;
    this.ultimoError = null;
  }

  public void registrarIntentoFallido(String error, int intentosMaximos) {
    this.intentos = this.intentos + 1;
    this.ultimoError = recortar(error);
    if (this.intentos >= intentosMaximos) {
      this.estado = ESTADO_FALLIDO;
    }
  }

  private static String recortar(String texto) {
    if (texto == null) {
      return null;
    }
    return texto.length() <= 1000 ? texto : texto.substring(0, 1000);
  }

  public UUID getId() {
    return id;
  }

  public String getTipo() {
    return tipo;
  }

  public int getVersionEvento() {
    return versionEvento;
  }

  public UUID getAgregadoId() {
    return agregadoId;
  }

  public String getAgregadoTipo() {
    return agregadoTipo;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public String getPayload() {
    return payload;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getEstado() {
    return estado;
  }

  public int getIntentos() {
    return intentos;
  }

  public Instant getOcurridoEn() {
    return ocurridoEn;
  }
}
