package co.gov.solicitudes.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/solicitudes/{id}/transiciones.
 *
 * <p>Una sola URI para las tres acciones porque el recurso conceptual es la transicion. Mantiene
 * la maquina de estados con un unico punto de entrada en vez de multiplicar endpoints que harian
 * lo mismo.
 *
 * <p>El motivo solo lo exige DEVOLVER, y esa exigencia la valida el dominio: ponerla aqui como
 * anotacion obligaria a declarar tres DTOs o a mentir sobre la obligatoriedad.
 */
public record TransicionRequest(
    @NotNull(message = "La accion es obligatoria.")
        @Pattern(
            regexp = "RESOLVER|DEVOLVER|CERRAR",
            message = "La accion debe ser RESOLVER, DEVOLVER o CERRAR.")
        String accion,
    @Size(max = 500, message = "El motivo no puede exceder 500 caracteres.") String motivo) {}
