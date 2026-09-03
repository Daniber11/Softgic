package co.gov.solicitudes.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de POST /api/v1/solicitudes.
 *
 * <p>No incluye solicitanteId: la identidad sale del token. Un campo asi en el cuerpo seria una
 * invitacion a suplantar.
 *
 * <p>La validacion de aqui es sintactica —presencia, longitud, formato— y produce 400. Las reglas
 * de negocio viven en el dominio y producen 422. Son dos cosas distintas y por eso responden con
 * codigos distintos.
 */
public record CrearSolicitudRequest(
    @NotBlank(message = "El asunto es obligatorio.")
        @Size(max = 200, message = "El asunto no puede exceder 200 caracteres.")
        String asunto,
    @NotBlank(message = "La descripcion es obligatoria.")
        @Size(max = 2000, message = "La descripcion no puede exceder 2000 caracteres.")
        String descripcion,
    @NotNull(message = "La categoria es obligatoria.") UUID categoriaId,
    @NotNull(message = "La prioridad es obligatoria.")
        @Pattern(regexp = "BAJA|MEDIA|ALTA", message = "La prioridad debe ser BAJA, MEDIA o ALTA.")
        String prioridad) {}
