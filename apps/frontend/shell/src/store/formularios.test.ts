import { describe, expect, it } from 'vitest';
import {
  crearSolicitudSchema,
  observacionSchema,
  transicionSchema,
} from '@shared/esquemas/formularios';

/**
 * Validacion Zod de los formularios de escritura.
 *
 * CLAUDE.md exige que toda respuesta del backend se valide con Zod antes de
 * entrar al estado; estas pruebas cubren el lado simetrico, la validacion de
 * ENTRADA antes de que un comando salga hacia el backend.
 */
describe('crearSolicitudSchema', () => {
  const valido = {
    asunto: 'Impresora sin tinta',
    descripcion: 'La impresora del piso 3 no imprime desde ayer.',
    categoriaId: '11111111-1111-4111-8111-111111111111',
    prioridad: 'ALTA' as const,
  };

  it('debeAceptarUnFormularioCompletoYValido', () => {
    const resultado = crearSolicitudSchema.safeParse(valido);
    expect(resultado.success).toBe(true);
  });

  it('debeRechazarAsuntoVacio', () => {
    const resultado = crearSolicitudSchema.safeParse({ ...valido, asunto: '   ' });
    expect(resultado.success).toBe(false);
  });

  it('debeRechazarAsuntoQueExcedeDoscientosCaracteres', () => {
    const resultado = crearSolicitudSchema.safeParse({ ...valido, asunto: 'a'.repeat(201) });
    expect(resultado.success).toBe(false);
  });

  it('debeRechazarCategoriaIdQueNoEsUnUuid', () => {
    const resultado = crearSolicitudSchema.safeParse({ ...valido, categoriaId: 'no-es-un-uuid' });
    expect(resultado.success).toBe(false);
  });

  it('debeRechazarPrioridadFueraDelCatalogo', () => {
    const resultado = crearSolicitudSchema.safeParse({ ...valido, prioridad: 'URGENTISIMA' });
    expect(resultado.success).toBe(false);
  });
});

describe('transicionSchema', () => {
  it('debeAceptarResolverSinMotivo', () => {
    const resultado = transicionSchema.safeParse({ accion: 'RESOLVER' });
    expect(resultado.success).toBe(true);
  });

  it('debeAceptarCerrarSinMotivo', () => {
    const resultado = transicionSchema.safeParse({ accion: 'CERRAR' });
    expect(resultado.success).toBe(true);
  });

  /** Refleja la regla del dominio (Solicitud.devolver exige un motivo). */
  it('debeRechazarDevolverSinMotivo', () => {
    const resultado = transicionSchema.safeParse({ accion: 'DEVOLVER' });
    expect(resultado.success).toBe(false);
  });

  it('debeRechazarDevolverConMotivoSoloEnBlanco', () => {
    const resultado = transicionSchema.safeParse({ accion: 'DEVOLVER', motivo: '   ' });
    expect(resultado.success).toBe(false);
  });

  it('debeAceptarDevolverConMotivo', () => {
    const resultado = transicionSchema.safeParse({
      accion: 'DEVOLVER',
      motivo: 'Falta evidencia.',
    });
    expect(resultado.success).toBe(true);
  });
});

describe('observacionSchema', () => {
  it('debeRechazarTextoVacio', () => {
    expect(observacionSchema.safeParse({ texto: '' }).success).toBe(false);
  });

  it('debeRechazarTextoQueExcedeMilCaracteres', () => {
    expect(observacionSchema.safeParse({ texto: 'a'.repeat(1001) }).success).toBe(false);
  });

  it('debeAceptarUnTextoValido', () => {
    expect(observacionSchema.safeParse({ texto: 'Se validó con el usuario.' }).success).toBe(true);
  });
});
