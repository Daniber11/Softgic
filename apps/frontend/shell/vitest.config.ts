import { defineConfig } from 'vitest/config';
import path from 'node:path';

/**
 * Configuracion de Vitest, separada del build de la app.
 *
 * Vitest no usa Rspack: trae su propio pipeline de transformacion. El alias
 * @shared se declara aqui tambien porque, sin el, las pruebas que importan
 * componentes de `shared/src` (EstadoVista, EstadoChip, los esquemas Zod)
 * no resolverian el especificador.
 */
export default defineConfig({
  resolve: {
    alias: {
      '@shared': path.resolve(__dirname, '../shared/src'),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    globals: false,
  },
  define: {
    __KEYCLOAK_ISSUER__: JSON.stringify('http://localhost:8080/realms/solicitudes-gov'),
    __SOLICITUDES_API_URL__: JSON.stringify('http://localhost:8081'),
    __INDICADORES_API_URL__: JSON.stringify('http://localhost:8082'),
  },
});
