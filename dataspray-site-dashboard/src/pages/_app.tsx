import type { AppProps } from 'next/app';
import Head from 'next/head';
import "@cloudscape-design/global-styles/index.css"

function MyApp({ Component, pageProps }: AppProps) {

  return (
    <>
      <Head>
        <meta name="viewport" content="initial-scale=1, width=device-width" />
        <link rel="icon" href="/favicon.ico" sizes="any" />
        <link rel="icon" href="/favicon.svg" type="image/svg+xml" />
      </Head>
      <Component {...pageProps} />
    </>
  );
}

export default MyApp
