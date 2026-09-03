package co.gov.solicitudes.infrastructure.adapter.out.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventoEntity, UUID> {

  /**
   * Toma un lote de eventos pendientes reservandolo para esta instancia.
   *
   * <p>UPDLOCK toma el bloqueo de actualizacion al leer, y READPAST hace que las filas ya
   * bloqueadas por otra instancia se salten en lugar de esperar. Juntos permiten que varias
   * replicas del servicio drenen el outbox en paralelo sin bloquearse ni entregar el mismo evento
   * dos veces. Es la clave de que el patron escale horizontalmente en SQL Server.
   *
   * <p>Es SQL nativo porque estas pistas son especificas del motor y JPQL no las expresa. Queda
   * como una dependencia declarada de SQL Server, contenida en esta unica consulta.
   */
  @Query(
      value =
          """
          SELECT TOP (:lote) *
          FROM outbox_evento WITH (UPDLOCK, READPAST)
          WHERE estado = 'PENDIENTE'
          ORDER BY ocurrido_en ASC
          """,
      nativeQuery = true)
  List<OutboxEventoEntity> tomarLotePendiente(@Param("lote") int lote);

  long countByEstado(String estado);
}
