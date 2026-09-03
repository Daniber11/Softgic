// =============================================================================
//  Shell - configuracion del host
//
//  Consume el remoto mfeIndicadores. La URL del remoto se toma de una variable
//  de entorno para que el mismo build sirva en local y en contenedor.
// =============================================================================

const path = require('node:path');
const rspack = require('@rspack/core');
const { ModuleFederationPlugin } = require('@module-federation/enhanced/rspack');
const { construirCompartidos } = require('../shared/federacion-compartida');
const { dependencies } = require('./package.json');

// El modo determina tambien el transform de JSX: con development:true SWC emite
// llamadas a jsxDEV, que no existe en el runtime de produccion de React. Fijarlo
// a mano rompe el build de produccion con una pantalla en blanco.
const esProduccion = process.env.NODE_ENV === 'production';

const PUERTO = 3000;
const URL_REMOTO_INDICADORES =
  process.env.MFE_INDICADORES_URL ?? 'http://localhost:3001/remoteEntry.js';

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

    new ModuleFederationPlugin({
      name: 'shell',
      remotes: {
        mfeIndicadores: `mfeIndicadores@${URL_REMOTO_INDICADORES}`,
      },
      shared: construirCompartidos(dependencies),
    }),
  ],

  devServer: {
    port: PUERTO,
    historyApiFallback: true,
    hot: false,
  },
};
