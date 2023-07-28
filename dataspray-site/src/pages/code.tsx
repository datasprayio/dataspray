import { useTheme } from '@mui/material';
import type { NextPage } from 'next';
import dynamic from 'next/dynamic';
import Head from 'next/head';

const Editor = dynamic(() => import('../dashboard/Editor'), { ssr: false });

const Home: NextPage = () => {
  const theme = useTheme();
  return (
    <>
      <Head>
        <title>Code editor</title>
        <meta name="mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-title" content="Code" />
        <link rel="apple-touch-icon" href="/vscode-web/code-192.png" />
        {/* Disable pinch zooming */}
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no" />
      </Head>
      <main>
        <Editor />
      </main>
    </>
  )
}

export default Home
