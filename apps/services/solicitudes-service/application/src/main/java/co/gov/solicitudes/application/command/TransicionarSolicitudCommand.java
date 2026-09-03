package co.gov.solicitudes.application.command;

import co.gov.solicitudes.domain.model.Accion;
import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.SolicitudId;

/**
 * Resolver, devolver o cerrar.
 *
 * <p>Un unico comando para las tres porque el recurso conceptual es la transicion; el discriminante
 * es la accion. El motivo solo lo exige DEVOLVER, y esa exigencia la valida el dominio.
 */
public record TransicionarSolicitudCommand(
    SolicitudId solicitudId, Accion accion, String motivo, Actor actor) {}
