// =============================================================================
//  MFE Indicadores - configuracion del remoto
//
//  Expone ./IndicadoresApp para que el shell lo consuma por federacion, y a la
//  vez arranca standalone en :3001 con su propio punto de montaje.
//
//  Consume `shell/authBridge` cuando corre federado. En modo standalone ese
//  remoto no existe, por eso el codigo que lo usa (auth/useSesion.ts) solo lo
//  importa dinamicamente cuando `modo === 'federado'`.
// =============================================================================

const path = require('node:path');
const rspack = require('@rspack/core');
const { ModuleFederationPlugin } = require('@module-federation/enhanced/rspack');
const { construirCompartidos } = require('../shared/federacion-compartida');
const { dependencies } = require('./package.json');

const esProduccion = process.env.NODE_ENV === 'production';

const PUERTO = 3001;
const URL_SHELL = process.env.SHELL_URL ?? 'http://localhost:3000/remoteEntry.js';

const VARIABLES_ENTORNO = {
  __KEYCLOAK_ISSUER__: JSON.stringify(
    process.env.KEYCLOAK_ISSUER ?? 'http://localhost:8080/realms/solicitudes-gov',
  ),
  __INDICADORES_API_URL__: JSON.stringify(
    process.env.INDICADORES_API_URL ?? 'http://localhost:8082',
  ),
};

module.exports = {
  entry: './src/index.ts',
  mode: esProduccion ? 'production' : 'development',

  output: {
    // 'auto' resuelve la URL de los fragmentos en tiempo de ejecucion. Fijarla a
    // mano rompe el remoto en cuanto cambia el origen desde el que se sirve.
    publicPath: 'auto',
    path: path.resolve(__dirname, 'dist'),
    clean: true,
  },

  resolve: {
    extensions: ['.ts', '.tsx', '.js', '.jsx'],
    alias: {
      '@shared': path.resolve(__dirname, '../shared/src'),
    },
  },

  module: {
    rules: [
      {
        test: /\.[jt]sx$/,
        loader: 'builtin:swc-loader',
        options: {
          jsc: {
            parser: { syntax: 'typescript', tsx: true },
            transform: { react: { runtime: 'automatic', development: !esProduccion, refresh: false } },
          },
        },
        type: 'javascript/auto',
      },
      {
        test: /\.ts$/,
        loader: 'builtin:swc-loader',
        options: {
          jsc: { parser: { syntax: 'typescript', tsx: false } },
        },
        type: 'javascript/auto',
      },
    ],
  },

  plugins: [
    new rspack.HtmlRspackPlugin({ template: './public/index.html' }),
    new rspack.DefinePlugin(VARIABLES_ENTORNO),
    new ModuleFederationPlugin({
      name: 'mfeIndicadores',
      filename: 'remoteEntry.js',
      exposes: {
        './IndicadoresApp': './src/IndicadoresApp.tsx',
      },
      remotes: {
        shell: `shell@${URL_SHELL}`,
      },
      shared: construirCompartidos(dependencies),
    }),
  ],

  devServer: {
    port: PUERTO,
    // Sin esta cabecera el shell, servido desde otro origen (:3000), no puede
    // descargar remoteEntry.js y la federacion falla por CORS, no por codigo.
    headers: { 'Access-Control-Allow-Origin': '*' },
    historyApiFallback: true,
    hot: false,
  },
};
