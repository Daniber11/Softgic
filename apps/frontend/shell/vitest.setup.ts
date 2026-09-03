import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';

/**
 * Limpieza explicita entre pruebas.
 *
 * @testing-library/react se auto-limpia SOLO si detecta un `afterEach`
 * global, algo que exige `test.globals: true` en la config de Vitest. Se
 * eligio no activar `globals` (para no depender de funciones implicitas
 * fuera de los imports explicitos que exige el resto del proyecto), asi que
 * la limpieza se registra aqui a mano. Sin esto, el DOM de una prueba queda
 * montado para la siguiente dentro del mismo archivo, y consultas como
 * `getByText` fallan por encontrar el mismo texto dos veces.
 */
afterEach(() => {
  cleanup();
});
