// =============================================================================
//  Shell - configuracion del host
//
//  Consume el remoto mfeIndicadores y le expone authBridge por federacion: el
//  shell es el unico duenio de la sesion OIDC (BLUEPRINT 9.2), y el remoto
//  nunca debe instanciar su propio UserManager cuando corre federado.
// =============================================================================

const path = require('node:path');
const rspack = require('@rspack/core');
const { ModuleFederationPlugin } = require('@module-federation/enhanced/rspack');
const { construirCompartidos } = require('../shared/federacion-compartida');
const { dependencies } = require('./package.json');

const esProduccion = process.env.NODE_ENV === 'production';

const PUERTO = 3000;
const URL_REMOTO_INDICADORES =
  process.env.MFE_INDICADORES_URL ?? 'http://localhost:3001/remoteEntry.js';

// Variables inyectadas en tiempo de build. Los valores por defecto son los
// del stack local; en el contenedor, docker compose las sobreescribe.
const VARIABLES_ENTORNO = {
  __KEYCLOAK_ISSUER__: JSON.stringify(
    process.env.KEYCLOAK_ISSUER ?? 'http://localhost:8080/realms/solicitudes-gov',
  ),
  __SOLICITUDES_API_URL__: JSON.stringify(
    process.env.SOLICITUDES_API_URL ?? 'http://localhost:8081',
  ),
  __INDICADORES_API_URL__: JSON.stringify(
    process.env.INDICADORES_API_URL ?? 'http://localhost:8082',
  ),
};

module.exports = {
  entry: './src/index.ts',
  mode: esProduccion ? 'production' : 'development',

  output: {
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
      name: 'shell',
      filename: 'remoteEntry.js',
      exposes: {
        // El puente de autenticacion: getToken(), getUsuario(), suscribirse().
        // Es la unica via por la que el remoto federado toca la sesion.
        './authBridge': './src/auth/authBridge.ts',
      },
      remotes: {
        mfeIndicadores: `mfeIndicadores@${URL_REMOTO_INDICADORES}`,
      },
      shared: construirCompartidos(dependencies),
    }),
  ],

  devServer: {
    port: PUERTO,
    headers: { 'Access-Control-Allow-Origin': '*' },
    historyApiFallback: true,
    hot: false,
  },
};
