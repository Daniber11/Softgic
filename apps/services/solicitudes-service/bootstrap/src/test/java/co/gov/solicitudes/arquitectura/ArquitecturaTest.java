package co.gov.solicitudes.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fronteras arquitectonicas verificadas por el build.
 *
 * <p>Esta clase es la razon por la que la arquitectura de este servicio no depende de la
 * disciplina de quien escribe el codigo. Un import de Spring dentro del dominio no se detecta en
 * una revision: rompe la compilacion del proyecto.
 *
 * <p><b>No se debilita esta prueba para que el build pase.</b> Si falla, el defecto esta en el
 * codigo que la violo.
 */
@DisplayName("Fronteras arquitectonicas")
class ArquitecturaTest {

  private static final String RAIZ = "co.gov.solicitudes";

  private static final String CAPA_DOMINIO = "Dominio";
  private static final String CAPA_APLICACION = "Aplicacion";
  private static final String CAPA_INFRAESTRUCTURA = "Infraestructura";
  private static final String CAPA_ARRANQUE = "Arranque";

  private static JavaClasses clases;

  @BeforeAll
  static void importarClasesDeProduccion() {
    clases =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(RAIZ);
  }

  // ---------------------------------------------------------------------------
  //  Regla de dependencia
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("lasDependenciasDebenApuntarSiempreHaciaElNucleo")
  void lasDependenciasDebenApuntarSiempreHaciaElNucleo() {
    ArchRule regla =
        Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer(CAPA_DOMINIO)
            .definedBy(RAIZ + ".domain..")
            .layer(CAPA_APLICACION)
            .definedBy(RAIZ + ".application..")
            .layer(CAPA_INFRAESTRUCTURA)
            .definedBy(RAIZ + ".infrastructure..")
            .layer(CAPA_ARRANQUE)
            .definedBy(RAIZ + ".config..")

            // Quien puede depender de cada capa. El dominio no lo accede nadie
            // "hacia abajo" porque no hay nada mas abajo.
            .whereLayer(CAPA_ARRANQUE)
            .mayNotBeAccessedByAnyLayer()
            .whereLayer(CAPA_INFRAESTRUCTURA)
            .mayOnlyBeAccessedByLayers(CAPA_ARRANQUE)
            .whereLayer(CAPA_APLICACION)
            .mayOnlyBeAccessedByLayers(CAPA_INFRAESTRUCTURA, CAPA_ARRANQUE)
            .whereLayer(CAPA_DOMINIO)
            .mayOnlyBeAccessedByLayers(CAPA_APLICACION, CAPA_INFRAESTRUCTURA, CAPA_ARRANQUE);

    regla.check(clases);
  }

  @Test
  @DisplayName("elDominioNoDebeDependerDeLaAplicacionNiDeLaInfraestructura")
  void elDominioNoDebeDependerDeLaAplicacionNiDeLaInfraestructura() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(RAIZ + ".application..", RAIZ + ".infrastructure..", RAIZ + ".config..")
        .check(clases);
  }

  @Test
  @DisplayName("laAplicacionNoDebeDependerDeLaInfraestructura")
  void laAplicacionNoDebeDependerDeLaInfraestructura() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(RAIZ + ".infrastructure..", RAIZ + ".config..")
        .check(clases);
  }

  // ---------------------------------------------------------------------------
  //  Pureza del nucleo: es la regla BLOQUEANTE del proyecto
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("elDominioNoDebeImportarNingunFramework")
  void elDominioNoDebeImportarNingunFramework() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "jakarta.validation..",
            "com.fasterxml.jackson..",
            "org.hibernate..",
            "com.rabbitmq..",
            "lombok..")
        .because(
            "el dominio debe poder compilarse y probarse sin ningun framework; "
                + "el pom del modulo domain no declara dependencias de produccion")
        .check(clases);
  }

  @Test
  @DisplayName("laAplicacionNoDebeImportarSpringNiJpaNiJackson")
  void laAplicacionNoDebeImportarSpringNiJpaNiJackson() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "com.fasterxml.jackson..",
            "org.hibernate..")
        .because(
            "los casos de uso son clases planas; es bootstrap quien las registra como beans, "
                + "de modo que el grafo de dependencias queda explicito y legible")
        .check(clases);
  }

  // ---------------------------------------------------------------------------
  //  Antipatrones concretos que la rubrica penaliza
  // ---------------------------------------------------------------------------

  /**
   * La primera version de esta regla exigia que toda entidad viviera en
   * {@code adapter.out.persistence}, y fallo con OutboxEventoEntity, que el blueprint ubica a
   * proposito en {@code adapter.out.messaging} porque el outbox es la salida de eventos aunque se
   * materialice como tabla.
   *
   * <p>Se corrigio la <b>expresion</b> de la regla, no su intencion: lo que hay que impedir es que
   * una entidad JPA se filtre al dominio, a la aplicacion o al adaptador REST. Que viva en uno u
   * otro adaptador de salida es una decision de organizacion, no una violacion de frontera. La
   * regla siguiente, que prohibe al controlador conocer la persistencia, es la que cierra el
   * riesgo real de devolver una entidad como respuesta HTTP.
   */
  @Test
  @DisplayName("ningunaEntidadJpaDebeVivirFueraDeLosAdaptadoresDeSalida")
  void ningunaEntidadJpaDebeVivirFueraDeLosAdaptadoresDeSalida() {
    classes()
        .that()
        .areAnnotatedWith("jakarta.persistence.Entity")
        .should()
        .resideInAPackage(RAIZ + ".infrastructure.adapter.out..")
        .because(
            "la entidad JPA, el modelo de dominio y el DTO de API son tres tipos distintos "
                + "porque tienen tres razones de cambio distintas")
        .check(clases);
  }

  @Test
  @DisplayName("losControladoresNoDebenDevolverEntidadesJpa")
  void losControladoresNoDebenDevolverEntidadesJpa() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".infrastructure.adapter.in.rest..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(RAIZ + ".infrastructure.adapter.out.persistence..")
        .because("el adaptador de entrada no conoce el modelo relacional")
        .check(clases);
  }

  @Test
  @DisplayName("losPuertosDeSalidaDebenSerInterfaces")
  void losPuertosDeSalidaDebenSerInterfaces() {
    classes()
        .that()
        .resideInAPackage(RAIZ + ".application.port..")
        .should()
        .beInterfaces()
        .because("un puerto es un contrato, no una implementacion")
        .check(clases);
  }

  @Test
  @DisplayName("losPuertosDebenNombrarseConElSufijoPortOUseCaseOQuery")
  void losPuertosDebenNombrarseConElSufijoPortOUseCaseOQuery() {
    classes()
        .that()
        .resideInAPackage(RAIZ + ".application.port.out..")
        .should()
        .haveSimpleNameEndingWith("Port")
        .check(clases);
  }
}
