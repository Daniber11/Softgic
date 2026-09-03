package co.gov.indicadores.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

  /**
   * Inserta la marca de procesado. Falla si el evento ya se proyecto.
   *
   * <p><b>Por que un INSERT nativo y no {@code save()}.</b> Es la correccion de un defecto real
   * detectado al ejecutar A5. Para una entidad con identificador ASIGNADO —aqui el eventId lo
   * genera el productor, no la base—, {@code save()} de Spring Data no puede saber si la fila es
   * nueva, asi que llama a {@code merge()}: hace un SELECT y, si la encuentra, un UPDATE. Nunca
   * se produce la violacion de clave primaria.
   *
   * <p>El sintoma era enganoso: {@code evento_procesado} no crecia, lo que parecia correcto,
   * mientras el hecho SI se insertaba y la proyeccion quedaba duplicada. La idempotencia
   * aparentaba funcionar sin hacerlo.
   *
   * <p>Con un INSERT explicito la semantica es inequivoca: o inserta, o viola la clave primaria y
   * revienta la transaccion completa, que es justamente lo que el patron necesita.
   */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO evento_procesado (event_id, tipo, procesado_en)
          VALUES (:eventId, :tipo, :procesadoEn)
          """,
      nativeQuery = true)
  void insertarMarca(
      @Param("eventId") UUID eventId,
      @Param("tipo") String tipo,
      @Param("procesadoEn") Instant procesadoEn);
}
