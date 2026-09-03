package co.gov.solicitudes.application.command;

import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.SolicitudId;

/** Un analista toma una solicitud de la bandeja. */
public record TomarSolicitudCommand(SolicitudId solicitudId, Actor analista) {}
