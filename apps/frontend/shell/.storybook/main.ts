import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { StorybookConfig } from '@storybook/react-webpack5';

const directorioActual = path.dirname(fileURLToPath(import.meta.url));

/**
 * Storybook documenta los dos componentes reutilizables que CLAUDE.md exige:
 * EstadoChip y EstadoVista. Viven en `shared/src`, asi que el alias @shared
 * se agrega tambien al webpack propio de Storybook (webpackFinal), igual que
 * en rspack.config.js para la app real.
 *
 * Se uso el framework react-webpack5, no Rspack: Storybook no tiene un
 * builder de Rspack mantenido en este momento (se verifico contra el
 * registro de npm antes de elegir). El builder de Storybook es un proceso de
 * documentacion separado del build de produccion de la app, asi que esto no
 * compromete la eleccion de Rspack para el shell en si.
 */
const config: StorybookConfig = {
  stories: ['../../shared/src/**/*.stories.@(ts|tsx)'],
  framework: {
    name: '@storybook/react-webpack5',
    options: {},
  },
  // 'react-docgen' (el generador por defecto) falla con
  // "callback(): The callback was already called" al analizar EstadoChip.tsx
  // (props de union discriminada) en Windows con webpack 5.110 + Node 24.
  // Se desactiva porque solo alimenta la tabla de props autogenerada; no es
  // necesaria para documentar los componentes via las historias mismas.
  typescript: {
    reactDocgen: false,
  },
  webpackFinal: async (webpackConfig) => {
    webpackConfig.resolve ??= {};
    webpackConfig.resolve.alias = {
      ...webpackConfig.resolve.alias,
      '@shared': path.resolve(directorioActual, '../../shared/src'),
    };
    // Webpack 5.110 activa su soporte nativo de TypeScript ("experiments.typescript:
    // auto") en Node >= 22.6 cuando no detecta un loader de TS registrado, pero ese
    // soporte nativo declara explicitamente que NO soporta JSX en archivos .tsx
    // (TypeScriptPlugin.js: "does not support .tsx/JSX"). El builder-webpack5 de
    // Storybook 10 no trae ya un loader de JSX por defecto, asi que se agrega
    // swc-loader (coherente con el resto del proyecto, que ya usa SWC via rspack)
    // y se desactiva el soporte nativo para que no compita con el.
    webpackConfig.experiments = {
      ...webpackConfig.experiments,
      typescript: false,
    };
    webpackConfig.module ??= { rules: [] };
    webpackConfig.module.rules ??= [];
    webpackConfig.module.rules.push({
      test: /\.tsx?$/,
      exclude: /node_modules/,
      loader: 'swc-loader',
      options: {
        jsc: {
          parser: { syntax: 'typescript', tsx: true },
          transform: { react: { runtime: 'automatic' } },
        },
      },
    });
    return webpackConfig;
  },
};

export default config;
