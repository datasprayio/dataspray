import { Box, Grid, Skeleton, Typography } from "@mui/material"
import GradientTypography from "./GradientTypography";

const Hero = () => {
  return (
    <Grid container spacing={2} alignItems='center' maxHeight=''>
      <Grid item xs={12}>
        <Box textAlign='center'>
          <Typography variant='h1' fontWeight='bold'>
            Easy event processing
          </Typography>
          <GradientTypography variant='h1' color='primary' fontWeight='bold'>
            DataSpray
          </GradientTypography>
        </Box>
      </Grid>
      <Grid item xs={12} sm={8}>
        <Skeleton variant="rectangular" width='80vw' height='80vh' />
      </Grid>
    </Grid>
  )
}

export default Hero;
