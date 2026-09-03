package co.gov.indicadores.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.indicadores.consumer.SobreEvento;
import co.gov.indicadores.consumer.TipoDeEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del contrato de entrada del proyector.
 *
 * <p>Cubre las dos unicas cosas que este servicio decide por si mismo: a que transicion corresponde
 * cada tipo de evento, y como reacciona ante un sobre que no reconoce. Todo lo demas —si la
 * transicion era valida, si el actor podia hacerla— ya lo decidio el productor.
 */
@DisplayName("Contrato de eventos del proyector")
class TipoDeEventoTest {

  @Test
  @DisplayName("debeMapearCadaEventoALaTransicionQueRepresenta")
  void debeMapearCadaEventoALaTransicionQueRepresenta() {
    assertThat(TipoDeEvento.desde("SolicitudRegistrada"))
        .get()
        .satisfies(
            t -> {
              assertThat(t.estadoOrigenKey()).isEqualTo(TipoDeEvento.ClaveEstado.NINGUNO);
              assertThat(t.estadoDestinoKey()).isEqualTo(TipoDeEvento.ClaveEstado.REGISTRADA);
              assertThat(t.rolKey()).isEqualTo(TipoDeEvento.ClaveRol.SOLICITANTE);
            });

    assertThat(TipoDeEvento.desde("SolicitudTomada"))
        .get()
        .satisfies(
            t -> {
              assertThat(t.estadoOrigenKey()).isEqualTo(TipoDeEvento.ClaveEstado.REGISTRADA);
              assertThat(t.estadoDestinoKey()).isEqualTo(TipoDeEvento.ClaveEstado.EN_ATENCION);
              assertThat(t.rolKey()).isEqualTo(TipoDeEvento.ClaveRol.ANALISTA);
            });

    assertThat(TipoDeEvento.desde("SolicitudDevuelta"))
        .get()
        .satisfies(
            t -> {
              assertThat(t.estadoOrigenKey()).isEqualTo(TipoDeEvento.ClaveEstado.RESUELTA);
              assertThat(t.estadoDestinoKey()).isEqualTo(TipoDeEvento.ClaveEstado.EN_ATENCION);
              assertThat(t.rolKey()).isEqualTo(TipoDeEvento.ClaveRol.SUPERVISOR);
            });

    assertThat(TipoDeEvento.desde("SolicitudCerrada"))
        .get()
        .satisfies(t -> assertThat(t.estadoDestinoKey()).isEqualTo(TipoDeEvento.ClaveEstado.CERRADA));
  }

  @Test
  @DisplayName("elRegistroDebeSerLaUnicaTransicionQuePartaDelCentinelaNinguno")
  void elRegistroDebeSerLaUnicaTransicionQuePartaDelCentinelaNinguno() {
    long desdeNinguno =
        Arrays.stream(TipoDeEvento.values())
            .filter(t -> t.estadoOrigenKey() == TipoDeEvento.ClaveEstado.NINGUNO)
            .count();

    assertThat(desdeNinguno)
        .as("solo el registro carece de estado previo")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("debeIgnorarUnTipoDesconocidoEnLugarDeFallar")
  void debeIgnorarUnTipoDesconocidoEnLugarDeFallar() {
    // Un productor mas nuevo puede emitir eventos que este consumidor no conoce.
    // Devolver vacio permite ignorarlos; lanzar los mandaria a la DLQ y obligaria
    // a desplegar los dos servicios a la vez.
    assertThat(TipoDeEvento.desde("SolicitudArchivada")).isEmpty();
  }

  @Test
  @DisplayName("elSobreNoDebeExponerIdentificadoresDePersona")
  void elSobreNoDebeExponerIdentificadoresDePersona() {
    // Verificacion de ADR-005 sobre el TIPO, no sobre una revision de codigo:
    // aunque el mensaje traiga analistaId, no hay donde deserializarlo, de modo
    // que es imposible persistirlo por descuido.
    String camposDeDatos =
        Arrays.stream(SobreEvento.Datos.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList()
            .toString();

    assertThat(camposDeDatos)
        .doesNotContain("analistaId")
        .doesNotContain("solicitanteId")
        .doesNotContain("supervisorId");
  }

  @Test
  @DisplayName("debeDeserializarUnSobreRealIgnorandoLosCamposQueNoUsa")
  void debeDeserializarUnSobreRealIgnorandoLosCamposQueNoUsa() throws Exception {
    // Sobre tal como lo escribe el productor, con campos de persona incluidos.
    String mensaje =
        """
        {"eventId":"3f1a0d1e-1111-4111-8111-111111111111","type":"SolicitudTomada","version":1,
         "occurredAt":"2026-09-03T10:15:30Z","aggregateId":"8f3c0d1e-2222-4222-8222-222222222222",
         "aggregateType":"Solicitud","correlationId":"abc-123","causationId":"abc-123",
         "producer":"solicitudes-service",
         "data":{"agregadoId":"8f3c0d1e-2222-4222-8222-222222222222","codigo":"SOL-2026-000001",
                 "categoriaId":"11111111-1111-4111-8111-111111111111","analistaId":"u-analista",
                 "ocurridoEn":"2026-09-03T10:15:30Z"}}
        """;

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    SobreEvento sobre = mapper.readValue(mensaje, SobreEvento.class);

    assertThat(sobre.type()).isEqualTo("SolicitudTomada");
    assertThat(sobre.correlationId()).isEqualTo("abc-123");
    assertThat(sobre.data().codigo()).isEqualTo("SOL-2026-000001");
    assertThat(sobre.data().categoriaId())
        .hasToString("11111111-1111-4111-8111-111111111111");
  }
}
