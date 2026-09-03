package co.gov.solicitudes.application.port.out;

import co.gov.solicitudes.domain.model.CodigoSolicitud;

/** Produce el siguiente codigo legible del tipo SOL-2026-000123. */
public interface GeneradorCodigoPort {
  CodigoSolicitud siguiente();
}
