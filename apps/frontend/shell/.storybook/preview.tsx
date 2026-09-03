import * as React from 'react';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import type { Preview } from '@storybook/react-webpack5';

const tema = createTheme({ palette: { primary: { main: '#1565c0' } } });

const preview: Preview = {
  decorators: [
    (Story) => (
      <ThemeProvider theme={tema}>
        <CssBaseline />
        <Story />
      </ThemeProvider>
    ),
  ],
  parameters: {
    controls: { expanded: true },
  },
};

export default preview;
