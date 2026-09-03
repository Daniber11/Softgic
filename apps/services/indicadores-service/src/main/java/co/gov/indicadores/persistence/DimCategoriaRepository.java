package co.gov.indicadores.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DimCategoriaRepository extends JpaRepository<DimCategoria, Integer> {

  Optional<DimCategoria> findByCategoriaId(UUID categoriaId);
}
