package co.gov.solicitudes.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/v1/solicitudes/{id}/observaciones. */
public record ObservacionRequest(
    @NotBlank(message = "El texto de la observacion es obligatorio.")
        @Size(max = 1000, message = "La observacion no puede exceder 1000 caracteres.")
        String texto) {}
