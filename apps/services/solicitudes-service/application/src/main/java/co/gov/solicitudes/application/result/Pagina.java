package co.gov.solicitudes.application.result;

import java.util.List;

/**
 * Pagina de resultados, independiente de cualquier framework de paginacion.
 *
 * <p>No se usa el Page de Spring Data porque eso arrastraria el framework hasta la capa de
 * aplicacion, que es justo lo que ArchUnit prohibe. El precio son estas pocas lineas; el beneficio
 * es que los casos de uso siguen siendo clases planas.
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamanio, long totalElementos) {

  public Pagina {
    contenido = List.copyOf(contenido);
  }

  public int totalPaginas() {
    return tamanio == 0 ? 0 : (int) Math.ceil((double) totalElementos / tamanio);
  }
}
