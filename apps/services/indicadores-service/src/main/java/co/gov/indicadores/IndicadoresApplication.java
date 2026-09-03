package co.gov.indicadores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de Indicadores: modelo de lectura alimentado por eventos.
 *
 * <p>Arquitectura en capas, no hexagonal (ADR-003). No hay puertos ni adaptadores porque no hay
 * dominio que aislar: este servicio transforma eventos en filas.
 */
@SpringBootApplication
public class IndicadoresApplication {

  public static void main(String[] args) {
    SpringApplication.run(IndicadoresApplication.class, args);
  }
}
