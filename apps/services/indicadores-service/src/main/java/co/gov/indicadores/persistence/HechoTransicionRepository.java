package co.gov.indicadores.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Escritura de la proyeccion y consultas agregadas del modelo de lectura. */
public interface HechoTransicionRepository extends JpaRepository<HechoTransicion, Long> {

  /**
   * Instante de la ultima transicion registrada del mismo expediente.
   *
   * <p>Sirve para calcular la medida de duracion. Se consulta la proyeccion y no el evento porque
   * el evento solo sabe de si mismo: la duracion es una propiedad de la secuencia, no del hecho
   * individual.
   */
  @Query(
      "SELECT MAX(h.ocurridoEn) FROM HechoTransicion h WHERE h.solicitudId = :solicitudId")
  Optional<Instant> ultimaTransicionDe(@Param("solicitudId") UUID solicitudId);

  /**
   * Solicitudes por estado ACTUAL.
   *
   * <p>La tabla de hechos guarda transiciones, no estados, de modo que el estado actual de un
   * expediente es el destino de su ultima transicion. La funcion de ventana selecciona esa fila
   * por solicitud y despues agrupa. Es SQL nativo porque JPQL no expresa funciones de ventana.
   *
   * <p>La alternativa habria sido mantener una segunda tabla con el estado vigente, pero eso
   * introduce un segundo lugar que puede quedar desincronizado con los hechos. Con el volumen de
   * esta prueba, derivarlo es correcto y tiene una sola fuente de verdad.
   */
  @Query(
      value =
          """
          SELECT e.codigo AS estado, COUNT(*) AS total
          FROM (
            SELECT h.solicitud_id, h.estado_destino_key,
                   ROW_NUMBER() OVER (PARTITION BY h.solicitud_id ORDER BY h.ocurrido_en DESC, h.id DESC) AS rn
            FROM hecho_transicion h
          ) ultima
          JOIN dim_estado e ON e.estado_key = ultima.estado_destino_key
          WHERE ultima.rn = 1
          GROUP BY e.codigo
          ORDER BY e.codigo
          """,
      nativeQuery = true)
  List<ConteoPorClave> contarPorEstadoActual();

  /** Solicitudes por categoria, contando expedientes distintos y no transiciones. */
  @Query(
      value =
          """
          SELECT c.codigo AS estado, COUNT(DISTINCT h.solicitud_id) AS total
          FROM hecho_transicion h
          JOIN dim_categoria c ON c.categoria_key = h.categoria_key
          GROUP BY c.codigo
          ORDER BY c.codigo
          """,
      nativeQuery = true)
  List<ConteoPorClave> contarPorCategoria();

  /**
   * Tendencia diaria de registros nuevos.
   *
   * <p>Se cuenta la transicion hacia REGISTRADA, que ocurre una sola vez por expediente. Contar
   * todas las transiciones daria una curva de actividad, no de demanda, y son dos indicadores
   * distintos.
   */
  @Query(
      value =
          """
          SELECT CONVERT(VARCHAR(10), f.fecha, 23) AS estado, COUNT(*) AS total
          FROM hecho_transicion h
          JOIN dim_fecha f ON f.fecha_key = h.fecha_key
          WHERE h.estado_destino_key = 1
            AND (:desde IS NULL OR f.fecha >= CAST(:desde AS DATE))
            AND (:hasta IS NULL OR f.fecha <= CAST(:hasta AS DATE))
          GROUP BY f.fecha
          ORDER BY f.fecha
          """,
      nativeQuery = true)
  List<ConteoPorClave> tendenciaDiaria(
      @Param("desde") String desde, @Param("hasta") String hasta);

  /** Tiempo medio, en minutos, desde que se toma una solicitud hasta que se resuelve. */
  @Query(
      value =
          """
          SELECT 'PROMEDIO_MINUTOS_RESOLUCION' AS estado,
                 COALESCE(AVG(CAST(h.duracion_minutos AS BIGINT)), 0) AS total
          FROM hecho_transicion h
          WHERE h.estado_destino_key = 3 AND h.duracion_minutos IS NOT NULL
          """,
      nativeQuery = true)
  List<ConteoPorClave> promedioMinutosHastaResolucion();

  /**
   * Asegura la fila del calendario antes de insertar el hecho.
   *
   * <p>La dimension de fecha se puebla de forma perezosa en lugar de sembrarse por rango. Un rango
   * fijo se agota: el dia que se pasara de la ultima fecha sembrada, la clave foranea rechazaria
   * el hecho y todos los eventos acabarian en la DLQ. Poblarla al vuelo no tiene esa fecha de
   * caducidad.
   *
   * <p>El WHERE NOT EXISTS la hace idempotente, de modo que dos consumidores concurrentes no
   * chocan.
   */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO dim_fecha (fecha_key, fecha, anio, mes, dia, dia_semana)
          SELECT :fechaKey, CAST(:fecha AS DATE), YEAR(CAST(:fecha AS DATE)),
                 MONTH(CAST(:fecha AS DATE)), DAY(CAST(:fecha AS DATE)),
                 DATEPART(WEEKDAY, CAST(:fecha AS DATE))
          WHERE NOT EXISTS (SELECT 1 FROM dim_fecha WHERE fecha_key = :fechaKey)
          """,
      nativeQuery = true)
  void asegurarFecha(@Param("fechaKey") int fechaKey, @Param("fecha") String fecha);

  /** Proyeccion de resultado de las consultas agregadas. */
  interface ConteoPorClave {
    String getEstado();

    long getTotal();
  }
}
