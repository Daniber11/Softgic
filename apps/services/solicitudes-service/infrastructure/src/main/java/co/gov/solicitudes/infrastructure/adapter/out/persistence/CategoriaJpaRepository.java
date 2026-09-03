package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaJpaRepository extends JpaRepository<CategoriaEntity, UUID> {

  List<CategoriaEntity> findByActivaTrueOrderByNombreAsc();
}
