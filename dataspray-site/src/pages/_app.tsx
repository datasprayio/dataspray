import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import { createTheme, ThemeProvider, useMediaQuery } from '@mui/material';
import CssBaseline from '@mui/material/CssBaseline';
import type { AppProps } from 'next/app';
import Head from 'next/head';
import { useMemo } from 'react';

function MyApp({ Component, pageProps }: AppProps) {
  const prefersDarkMode = useMediaQuery('(prefers-color-scheme: dark)');
  const theme = useMemo(
    () =>
      createTheme({
        palette: {
          mode: prefersDarkMode ? 'dark' : 'light',
          primary: {
            main: '#388e3c',
          },
          secondary: {
            main: '#f06292',
          },
        },
      }),
    [prefersDarkMode],
  );

  return (
    <>
      <Head>
        <meta name="viewport" content="initial-scale=1, width=device-width" />
        <link rel="icon" href="/favicon.ico" />
      </Head>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Component {...pageProps} />
      </ThemeProvider>
    </>
  );
}

export default MyApp
