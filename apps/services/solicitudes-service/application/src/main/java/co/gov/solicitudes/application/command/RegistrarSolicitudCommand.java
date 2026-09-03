package co.gov.solicitudes.application.command;

import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.CategoriaId;
import co.gov.solicitudes.domain.model.Prioridad;

/**
 * Entrada del caso de uso de registro.
 *
 * <p>Es un tipo propio y no el DTO HTTP: desacopla la firma del caso de uso del contrato de
 * transporte, de modo que cambiar el JSON de entrada no obliga a tocar la aplicacion.
 */
public record RegistrarSolicitudCommand(
    String asunto, String descripcion, CategoriaId categoriaId, Prioridad prioridad, Actor solicitante) {}
