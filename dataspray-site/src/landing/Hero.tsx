import { Box, Grid, Skeleton, Typography } from "@mui/material"
import GradientTypography from "./GradientTypography";

const Hero = () => {
  return (
    <Grid container spacing={2} alignItems='center' maxHeight=''>
      <Grid item xs={12} sm={5}>
        <Box textAlign='center'>
          <GradientTypography variant='h1' color='primary' fontWeight='bold'>
            Real-time action
          </GradientTypography>
          <Typography variant='h1' fontWeight='bold'>
            from your system events
          </Typography>
        </Box>
      </Grid>
      <Grid item xs={12} sm={7}>
        <Skeleton variant="rectangular" width='80vw' height='80vh' />
      </Grid>
    </Grid>
  )
}

export default Hero;
