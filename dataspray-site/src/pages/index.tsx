import { Box, Typography } from '@mui/material'
import type { NextPage } from 'next'
import Head from 'next/head'
import Hero from '../landing/Hero'

const Home: NextPage = () => {
  return (
    <>
      <Head>
        <title>DataSpray</title>
      </Head>
      <main>
        <Hero />
      </main>
    </>
  )
}

export default Home
