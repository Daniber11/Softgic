// =============================================================================
//  MFE Indicadores - configuracion del remoto
//
//  Expone ./IndicadoresApp para que el shell lo consuma por federacion, y a la
//  vez arranca standalone en :3001 con su propio punto de montaje.
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

const PUERTO = 3001;

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
      name: 'mfeIndicadores',
      filename: 'remoteEntry.js',
      exposes: {
        './IndicadoresApp': './src/IndicadoresApp.tsx',
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
