import { createTheme, type Theme } from '@mui/material';

// =============================================================================
//  Tema visual compartido por el shell y el microfrontend remoto.
//
//  Vive en `shared` y no en cada app a proposito: dos createTheme distintos
//  producirian dos escalas de color y de sombra ligeramente distintas, y la
//  diferencia se nota justo en la frontera federada (una tarjeta del remoto
//  con otro radio o con otra sombra que las del host delata la costura).
//
//  El objetivo del diseño es que una herramienta de gobierno se vea seria sin
//  verse plana: superficies con elevacion real, jerarquia tipografica marcada,
//  y color usado para significar estado, no para decorar.
// =============================================================================

const AZUL_INSTITUCIONAL = '#0B4F9E';
const AZUL_PROFUNDO = '#083A75';
const AZUL_CLARO = '#3B82D6';

/** Gris azulado: mas calido que el gris puro, evita el aspecto "wireframe". */
const TINTA = '#0F172A';
const TINTA_SUAVE = '#475569';
const BORDE = '#E2E8F0';
const FONDO = '#F6F8FB';

/**
 * Sombras en dos capas (uena de contacto corta y una de difusion amplia).
 * Una sola sombra plana es exactamente lo que hace que una interfaz se vea
 * "de plantilla"; el par contacto + difusion es lo que da sensacion de
 * material real.
 */
const SOMBRA_TARJETA = '0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 12px rgba(15, 23, 42, 0.06)';
const SOMBRA_ELEVADA = '0 2px 4px rgba(15, 23, 42, 0.06), 0 12px 28px rgba(15, 23, 42, 0.10)';

export const temaSolicitudes: Theme = createTheme({
  palette: {
    primary: { main: AZUL_INSTITUCIONAL, dark: AZUL_PROFUNDO, light: AZUL_CLARO },
    secondary: { main: '#0E9F8A' },
    success: { main: '#0F9D58' },
    warning: { main: '#B45309' },
    error: { main: '#C2354B' },
    background: { default: FONDO, paper: '#FFFFFF' },
    text: { primary: TINTA, secondary: TINTA_SUAVE },
    divider: BORDE,
  },

  shape: { borderRadius: 10 },

  typography: {
    fontFamily: '"Segoe UI", system-ui, -apple-system, "Helvetica Neue", Arial, sans-serif',
    // Titulos con tracking negativo: a tamaños grandes el espaciado por
    // defecto se ve suelto y amateur.
    h5: { fontWeight: 700, letterSpacing: '-0.02em' },
    h6: { fontWeight: 700, letterSpacing: '-0.01em' },
    subtitle2: { fontWeight: 600, letterSpacing: '0.01em' },
    button: { fontWeight: 600, textTransform: 'none' },
    caption: { color: TINTA_SUAVE },
  },

  components: {
    MuiAppBar: {
      styleOverrides: {
        root: {
          // Degradado sutil, no decorativo: da profundidad a la barra sin
          // convertirla en el elemento mas llamativo de la pantalla.
          backgroundImage: `linear-gradient(90deg, ${AZUL_PROFUNDO} 0%, ${AZUL_INSTITUCIONAL} 100%)`,
          boxShadow: '0 1px 0 rgba(255,255,255,0.08) inset, 0 2px 12px rgba(8, 58, 117, 0.28)',
        },
      },
    },

    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
        outlined: { borderColor: BORDE },
      },
    },

    // Las tarjetas de contenido llevan borde ademas de sombra: sobre un fondo
    // claro la sombra sola no define el limite con suficiente contraste.
    MuiCard: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: { border: `1px solid ${BORDE}`, boxShadow: SOMBRA_TARJETA },
      },
    },

    MuiTableContainer: {
      styleOverrides: {
        root: { border: `1px solid ${BORDE}`, boxShadow: SOMBRA_TARJETA, borderRadius: 12 },
      },
    },

    MuiTableHead: {
      styleOverrides: {
        root: {
          backgroundColor: '#F1F5F9',
          '& .MuiTableCell-head': {
            fontWeight: 700,
            fontSize: '0.78rem',
            letterSpacing: '0.04em',
            textTransform: 'uppercase',
            color: TINTA_SUAVE,
            borderBottom: `1px solid ${BORDE}`,
          },
        },
      },
    },

    MuiTableRow: {
      styleOverrides: {
        root: {
          transition: 'background-color 120ms ease',
          '&:last-child .MuiTableCell-root': { borderBottom: 'none' },
        },
      },
    },

    MuiTableCell: {
      styleOverrides: {
        root: { borderBottom: `1px solid ${BORDE}`, paddingTop: 14, paddingBottom: 14 },
      },
    },

    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, letterSpacing: '0.01em' },
        sizeSmall: { height: 24 },
      },
    },

    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { borderRadius: 8, paddingInline: 18 },
        containedPrimary: {
          backgroundImage: `linear-gradient(180deg, ${AZUL_CLARO} 0%, ${AZUL_INSTITUCIONAL} 100%)`,
          boxShadow: '0 1px 2px rgba(8,58,117,0.24)',
          '&:hover': { backgroundImage: `linear-gradient(180deg, ${AZUL_INSTITUCIONAL} 0%, ${AZUL_PROFUNDO} 100%)` },
        },
      },
    },

    MuiTab: {
      styleOverrides: {
        root: {
          fontWeight: 600,
          textTransform: 'none',
          fontSize: '0.95rem',
          minHeight: 48,
          opacity: 0.82,
          '&.Mui-selected': { opacity: 1 },
        },
      },
    },

    MuiDialog: {
      styleOverrides: { paper: { borderRadius: 14, boxShadow: SOMBRA_ELEVADA } },
    },

    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: '#FFFFFF',
          '& fieldset': { borderColor: BORDE },
          '&:hover fieldset': { borderColor: AZUL_CLARO },
        },
      },
    },

    MuiLink: { styleOverrides: { root: { fontWeight: 600, textDecorationColor: 'transparent' } } },
  },
});
