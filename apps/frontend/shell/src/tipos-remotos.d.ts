// =============================================================================
//  Declaracion de tipos de los modulos remotos.
//
//  Un modulo federado se resuelve en tiempo de ejecucion, asi que TypeScript no
//  puede inferirlo del sistema de archivos. Sin esta declaracion, importar el
//  remoto obligaria a un `any`, que esta prohibido por CLAUDE.md.
//
//  El contrato declarado aqui es justamente eso: un contrato. Si el remoto
//  cambia la forma de sus props, esta firma debe cambiar con el, y compilar
//  falla si el shell la usa mal.
// =============================================================================

declare module 'mfeIndicadores/IndicadoresApp' {
  interface IndicadoresAppProps {
    readonly modo: 'federado' | 'standalone';
  }

  const IndicadoresApp: (props: IndicadoresAppProps) => import('react').JSX.Element;
  export default IndicadoresApp;
}

