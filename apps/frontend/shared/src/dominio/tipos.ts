/**
 * Tipos de dominio compartidos entre el shell y los microfrontends remotos.
 *
 * Son una copia deliberada del vocabulario del backend, no una dependencia
 * compartida en tiempo de ejecucion: el frontend no importa nada del backend
 * Java. Mantenerlos como texto plano (no como un paquete publicado) es
 * consistente con como ya se comparte `federacion-compartida.js` en este
 * monorepo: cada app se compila con su propia copia.
 */

export const ESTADOS_SOLICITUD = [
  'REGISTRADA',
  'EN_ATENCION',
  'RESUELTA',
  'CERRADA',
] as const;

export type EstadoSolicitud = (typeof ESTADOS_SOLICITUD)[number];

export const PRIORIDADES = ['BAJA', 'MEDIA', 'ALTA'] as const;

export type Prioridad = (typeof PRIORIDADES)[number];

export const ROLES = ['SOLICITANTE', 'ANALISTA', 'SUPERVISOR'] as const;

export type Rol = (typeof ROLES)[number];

export const ACCIONES_TRANSICION = ['RESOLVER', 'DEVOLVER', 'CERRAR'] as const;

export type AccionTransicion = (typeof ACCIONES_TRANSICION)[number];

/**
 * Etiquetas legibles para cada estado y prioridad.
 *
 * Vive aqui y no en cada componente porque el codigo del estado (REGISTRADA)
 * y su etiqueta (Registrada) son el mismo concepto visto desde dos lugares
 * distintos de la interfaz: la bandeja, el detalle, la linea de tiempo.
 */
export const ETIQUETA_ESTADO: Record<EstadoSolicitud, string> = {
  REGISTRADA: 'Registrada',
  EN_ATENCION: 'En atención',
  RESUELTA: 'Resuelta',
  CERRADA: 'Cerrada',
};

export const ETIQUETA_PRIORIDAD: Record<Prioridad, string> = {
  BAJA: 'Baja',
  MEDIA: 'Media',
  ALTA: 'Alta',
};

export const ETIQUETA_ROL: Record<Rol, string> = {
  SOLICITANTE: 'Solicitante',
  ANALISTA: 'Analista',
  SUPERVISOR: 'Supervisor',
};
