// El punto de entrada delega en bootstrap con un import() dinamico.
//
// No es un adorno: crea la frontera asincrona que Module Federation necesita
// para negociar las dependencias compartidas ANTES de que se evalue el primer
// modulo de la aplicacion. Sin esta indireccion, React se carga antes de que la
// negociacion ocurra y el "shared: singleton" deja de tener efecto.
import('./bootstrap');

export {};
