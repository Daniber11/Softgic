// =============================================================================
//  Modulos compartidos entre el shell y los microfrontends remotos.
//
//  POR QUE ESTE ARCHIVO EXISTE Y ES UNICO
//
//  Si el host y el remoto declaran listas de `shared` distintas, cada uno carga
//  su propia copia de React o de Emotion. Con Emotion duplicado, MUI deja de
//  encontrar su contexto de tema: los estilos desaparecen o el remoto revienta
//  con un error de contexto imposible de rastrear hasta su causa.
//
//  La forma de garantizar que ambas listas sean identicas no es la disciplina,
//  es que solo exista una lista. Este archivo es esa lista.
//
//  `singleton: true`      una sola instancia en toda la pagina, la comparta quien
//                         la comparta.
//  `requiredVersion`      version exacta tomada de las dependencias. Si un
//                         remoto llega con otra, la consola avisa en lugar de
//                         fallar de forma silenciosa.
//  `eager: false`         la dependencia se carga por el grafo asincrono, que es
//                         la razon por la que el punto de entrada delega en
//                         bootstrap con un import() dinamico.
// =============================================================================

/**
 * @param {Record<string, string>} dependencias  el bloque "dependencies" del package.json
 * @returns {Record<string, object>} configuracion de `shared` para ModuleFederationPlugin
 */
function construirCompartidos(dependencias) {
  const comoSingleton = (paquete) => ({
    singleton: true,

    // `version` declara la version que ESTE build aporta; `requiredVersion` la
    // que exige de los demas. Declarar ambas no es redundante: MUI 7 resuelve a
    // `@mui/material/esm/index.js`, y ese subdirectorio no tiene package.json
    // con version, asi que Module Federation no puede deducirla y descarta el
    // modulo del share scope sin fallar. El sintoma es MUI descargado dos veces
    // mientras el manifiesto asegura que es singleton.
    version: dependencias[paquete],
    requiredVersion: dependencias[paquete],
    eager: false,
  });

  // REGLA QUE HAY QUE RESPETAR AL ESCRIBIR CODIGO FEDERADO
  //
  // La clave de `shared` debe coincidir exactamente con el especificador que se
  // escribe en el import. `'@mui/material'` NO cubre
  // `import Alert from '@mui/material/Alert'`: son especificadores distintos y
  // el modulo profundo se empaqueta aparte, una copia en el host y otra en cada
  // remoto.
  //
  // Lo peligroso es que nada falla a la vista: el tema sigue llegando porque lo
  // transporta Emotion, que si es unico. Se detecto en la fase 2 inspeccionando
  // el share scope en tiempo de ejecucion, no mirando la pantalla.
  //
  // Por eso, en shell y remotos, MUI se importa SIEMPRE desde el barril:
  //     import { Alert, Box, createTheme } from '@mui/material';
  // Declarar ademas una clave '@mui/material/' se probo y quedo descartado: el
  // manifiesto la expande por submodulo, pero esas entradas no llegan al share
  // scope, de modo que es configuracion muerta que aparenta resolver el problema.

  return {
    // El nucleo de React debe ser unico o los hooks fallan al cruzar la frontera
    // del remoto ("invalid hook call" es siempre React duplicado).
    react: comoSingleton('react'),
    'react-dom': comoSingleton('react-dom'),

    // Enrutado: dos instancias competirian por el mismo history del navegador.
    'react-router-dom': comoSingleton('react-router-dom'),

    // MUI y Emotion. Emotion es el caso critico descrito arriba.
    '@mui/material': comoSingleton('@mui/material'),
    '@emotion/react': comoSingleton('@emotion/react'),
    '@emotion/styled': comoSingleton('@emotion/styled'),

    // Estado: el remoto consume el store del host, no crea el suyo.
    '@reduxjs/toolkit': comoSingleton('@reduxjs/toolkit'),
    'react-redux': comoSingleton('react-redux'),
  };
}

module.exports = { construirCompartidos };
