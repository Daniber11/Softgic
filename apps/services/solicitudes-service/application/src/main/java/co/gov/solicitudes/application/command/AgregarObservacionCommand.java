package co.gov.solicitudes.application.command;

import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.SolicitudId;

/** Un analista o un supervisor deja un comentario en el expediente. */
public record AgregarObservacionCommand(SolicitudId solicitudId, String texto, Actor autor) {}
