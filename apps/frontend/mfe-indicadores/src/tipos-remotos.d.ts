// =============================================================================
//  Declaracion de tipos del modulo remoto consumido desde el shell.
//
//  Espejo de shell/src/tipos-remotos.d.ts: alli se declara el contrato que el
//  shell exige del remoto, aqui se declara el contrato que este remoto exige
//  del shell. Los dos existen porque cada build es independiente y ninguno ve
//  el codigo fuente del otro en tiempo de compilacion.
// =============================================================================

declare module 'shell/authBridge' {
  export interface UsuarioBridge {
    readonly actorId: string;
    readonly roles: readonly ('SOLICITANTE' | 'ANALISTA' | 'SUPERVISOR')[];
  }

  interface AuthBridge {
    /** Se resuelve cuando la lectura inicial del usuario ya esta disponible. */
    readonly listo: Promise<void>;
    getToken(): string | null;
    getUsuario(): UsuarioBridge | null;
    suscribir(callback: () => void): () => void;
  }

  const authBridge: AuthBridge;
  export default authBridge;
}

declare const __KEYCLOAK_ISSUER__: string;
declare const __INDICADORES_API_URL__: string;
