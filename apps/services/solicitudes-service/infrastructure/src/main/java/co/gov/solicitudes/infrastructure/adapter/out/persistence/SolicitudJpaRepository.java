package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data. Detalle de infraestructura: la aplicacion solo ve el puerto. */
public interface SolicitudJpaRepository extends JpaRepository<SolicitudEntity, UUID> {

  boolean existsByCodigo(String codigo);

  Optional<SolicitudEntity> findByCodigo(String codigo);

  /**
   * Consulta de la bandeja con filtros opcionales.
   *
   * <p>Cada criterio se anula con "parametro is null", que permite una sola consulta en lugar de
   * una Specification dinamica. Con cinco filtros conocidos y fijos, la Specification seria mas
   * maquinaria de la que el problema pide.
   */
  @Query(
      """
      SELECT s FROM SolicitudEntity s
      WHERE (:estado IS NULL OR s.estado = :estado)
        AND (:categoriaId IS NULL OR s.categoriaId = :categoriaId)
        AND (:prioridad IS NULL OR s.prioridad = :prioridad)
        AND (:desde IS NULL OR s.creadaEn >= :desde)
        AND (:hasta IS NULL OR s.creadaEn <= :hasta)
        AND (:solicitanteId IS NULL OR s.solicitanteId = :solicitanteId)
      """)
  Page<SolicitudEntity> buscarConFiltros(
      @Param("estado") String estado,
      @Param("categoriaId") UUID categoriaId,
      @Param("prioridad") String prioridad,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      @Param("solicitanteId") String solicitanteId,
      Pageable pageable);

  /** Ultimo consecutivo emitido en el anio, para generar el codigo legible. */
  @Query("SELECT MAX(s.codigo) FROM SolicitudEntity s WHERE s.codigo LIKE :prefijo")
  Optional<String> ultimoCodigoDelAnio(@Param("prefijo") String prefijo);
}
