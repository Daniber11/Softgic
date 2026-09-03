package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotenciaJpaRepository extends JpaRepository<IdempotenciaComandoEntity, String> {}
