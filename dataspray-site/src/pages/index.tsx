import ConstructionIcon from '@mui/icons-material/Construction';
import { Box, Container, Link, Typography, useTheme } from '@mui/material';
import type { NextPage } from 'next';
import Head from 'next/head';
import GradientTypography from '../landing/GradientTypography';
import Image from 'next/image'

const Home: NextPage = () => {
  const theme = useTheme();
  return (
    <>
      <Head>
        <title>DataSpray</title>
      </Head>
      <main>
        <Container maxWidth='md'>
          <Box textAlign='center' margin={theme.spacing(8, 0)} >
            <Box>
              <picture>
                <source media="(prefers-color-scheme: dark)" srcSet="/logo/logo-and-title-dark.png" />
                <img alt='DataSpray' height={150} src="/logo/logo-and-title-light.png" />
              </picture>
            </Box>

            <br />
            <br />
            <br />

            <GradientTypography variant='h3'>
              Stream processing
            </GradientTypography>
            <Typography variant='h3'>
              developer toolkit
            </Typography>
          </Box>

          <Box textAlign='center' margin={theme.spacing(8, 0)}>
            <ConstructionIcon fontSize='inherit' sx={{ fontSize: 70 }} />
            <Typography variant='h5'>
              Check out the development on <Link href='https://github.com/datasprayio/dataspray' color='secondary'>GitHub</Link>
            </Typography>
          </Box>

          {/* <Block mirror title={(<>
            <GradientTypography variant='h5'>Visualize</GradientTypography> your dataflow
          </>)} description={(<>
            <ul>
              <li>See your streaming queues with your processors</li>
              <li>Monitor throughput, bottlenecks, error rates</li>
            </ul>
          </>)} />

          <Block title={(<>
            Re-process <GradientTypography variant='h5'>historical data</GradientTypography> to inform your decisions
          </>)} description={(<>
            <ul>
              <li>Run periodic jobs over the entire history</li>
              <li>Use simple SQL to aggregate and query data you need</li>
              <li>Feed the results back into you pipeline</li>
            </ul>
          </>)} />

          <Block mirror title={(<>
            Bring your own <GradientTypography variant='h5'>code</GradientTypography>
          </>)} description={(<>
            <ul>
              <li>Own and manage your Git repo with entire pipeline</li>
              <li>Write in Java, Python, JS/TS, or IDML </li>
              <li>Use your own IDE or use our online editor</li>
              <li>No vendor lock-in, eject generated boilerplate code and run on your own cluster</li>
            </ul>
          </>)} /> */}

          {/* <Block mirror title={(<>
            Affordable <GradientTypography variant='h5'>pay-for-use</GradientTypography> cloud
          </>)} description={(<>
            Ideal for deploymet
          </>)} />

          <Block mirror title={(<>
            Affordable <GradientTypography variant='h5'>pay-for-use</GradientTypography> cloud
          </>)} description={(<>
            Ideal for deploymet
          </>)} /> */}
        </Container>
      </main>
    </>
  )
}

export default Home
