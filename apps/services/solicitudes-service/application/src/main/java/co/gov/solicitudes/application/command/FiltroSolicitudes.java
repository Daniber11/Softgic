package co.gov.solicitudes.application.command;

import co.gov.solicitudes.domain.model.EstadoSolicitud;
import co.gov.solicitudes.domain.model.Prioridad;
import java.time.Instant;
import java.util.UUID;

/**
 * Criterios de busqueda de la bandeja.
 *
 * <p>{@code soloDelSolicitante} no es un filtro que el usuario elija: lo impone el caso de uso
 * cuando quien consulta tiene rol SOLICITANTE, porque solo puede ver lo suyo. Modelarlo aqui, y no
 * en el controlador, impide que una ruta nueva olvide aplicarlo.
 *
 * <p>Vive fuera del paquete de puertos porque un puerto es una interfaz y nada mas; los tipos que
 * viajan por el son comandos y resultados, y tienen su propio lugar.
 */
public record FiltroSolicitudes(
    EstadoSolicitud estado,
    UUID categoriaId,
    Prioridad prioridad,
    Instant desde,
    Instant hasta,
    String soloDelSolicitante) {}
