package co.gov.solicitudes.infrastructure.adapter.in.rest.dto;

import co.gov.solicitudes.application.result.Pagina;
import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de paginacion del API.
 *
 * <p>Es un tipo propio y no el Page de Spring Data. El Page serializa una decena de campos
 * internos —pageable, sort, first, last, numberOfElements— que atarian el contrato publico a la
 * representacion interna de una libreria. Aqui se publican los cinco campos que el reto pide y
 * nada mas.
 */
public record PaginaResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <D, R> PaginaResponse<R> desde(
      Pagina<D> pagina, Function<D, R> transformacion) {
    return new PaginaResponse<>(
        pagina.contenido().stream().map(transformacion).toList(),
        pagina.pagina(),
        pagina.tamanio(),
        pagina.totalElementos(),
        pagina.totalPaginas());
  }
}
