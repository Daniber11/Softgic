package co.gov.solicitudes.application.port.in;

import co.gov.solicitudes.domain.model.Categoria;
import java.util.List;

public interface ConsultarCategoriasQuery {
  List<Categoria> listarActivas();
}
