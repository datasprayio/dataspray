import { Box, Container, Grid, Typography, useTheme } from '@mui/material';
import type { NextPage } from 'next';
import Head from 'next/head';
import Block from '../landing/Block';
import GradientTypography from '../landing/GradientTypography';

const Home: NextPage = () => {
  const theme = useTheme();
  return (
    <>
      <Head>
        <title>DataSpray</title>
      </Head>
      <main>
        <Container maxWidth='md'>
          <Box margin={theme.spacing(8, 0)}>
            <GradientTypography variant='h2' component='h1'>
              Stream processing
            </GradientTypography>
            <Typography variant='h2' component='h1'>
              developer toolkit
            </Typography>
          </Box>

          <Block mirror title={(<>
            Write the <GradientTypography variant='h5' component='h2'>logic</GradientTypography> not boilerplate
          </>)} description={(<>
            Ideal for deploymet
          </>)} />

          <Block mirror title={(<>
            <GradientTypography variant='h5' component='h2'>Visualize</GradientTypography> your dataflow
          </>)} description={(<>
            <ul>
              <li>See your streaming queues with your processors</li>
              <li>Monitor throughput, bottlenecks, error rates</li>
            </ul>
          </>)} />

          <Block title={(<>
            Re-process <GradientTypography variant='h5' component='h2'>historical data</GradientTypography> to inform your decisions
          </>)} description={(<>
            <ul>
              <li>Run periodic jobs over the entire history</li>
              <li>Use simple SQL to aggregate and query data you need</li>
              <li>Feed the results back into you pipeline</li>
            </ul>
          </>)} />

          <Block mirror title={(<>
            Bring your own <GradientTypography variant='h5' component='h2'>code</GradientTypography>
          </>)} description={(<>
            <ul>
              <li>Own and manage your Git repo with entire pipeline</li>
              <li>Write in Java, Python, JS/TS, or IDML </li>
              <li>Use your own IDE or use our online editor</li>
              <li>No vendor lock-in, eject generated boilerplate code and run on your own cluster</li>
            </ul>
          </>)} />

          <Block mirror title={(<>
            Affordable <GradientTypography variant='h5' component='h2'>pay-for-use</GradientTypography> cloud
          </>)} description={(<>
            Ideal for deploymet
          </>)} />

          <Block mirror title={(<>
            Affordable <GradientTypography variant='h5' component='h2'>pay-for-use</GradientTypography> cloud
          </>)} description={(<>
            Ideal for deploymet
          </>)} />
        </Container>
      </main>
    </>
  )
}

export default Home
