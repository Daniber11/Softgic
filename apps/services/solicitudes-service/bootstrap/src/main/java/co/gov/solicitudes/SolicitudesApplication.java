package co.gov.solicitudes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de arranque del Servicio de Solicitudes.
 *
 * <p>Los escaneos se declaran de forma explicita porque las entidades y los repositorios viven en
 * el modulo de infraestructura, fuera del paquete de esta clase. Dejarlo al escaneo por defecto
 * funcionaria solo mientras los paquetes coincidieran por casualidad.
 *
 * <p>{@code @EnableScheduling} habilita el publicador del outbox. Sin el, los eventos se
 * escribirian en la tabla y nunca saldrian hacia el broker: un fallo silencioso y dificil de
 * diagnosticar, porque el registro de solicitudes seguiria funcionando con normalidad.
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "co.gov.solicitudes.infrastructure.adapter.out")
@EnableJpaRepositories(basePackages = "co.gov.solicitudes.infrastructure.adapter.out")
public class SolicitudesApplication {

  public static void main(String[] args) {
    SpringApplication.run(SolicitudesApplication.class, args);
  }
}
