import { Grid, Typography } from "@mui/material";

const Block = (props: {
  title: React.ReactNode;
  description: React.ReactNode;
  preview?: React.ReactNode;
  mirror?: boolean;
}) => {
  const titleCmpt = (
    <Grid key='title' item xs={12} sm={5}>
      <Typography variant='h5' component='h2'>
        {props.title}
      </Typography>
      <Typography variant='body1'>
        {props.description}
      </Typography>
    </Grid>
  );
  const previewCmpt = (
    <Grid key='preview' item xs={12} sm={7}>
      {props.preview}
    </Grid>
  );
  return (
    <Grid container spacing={2}>
      {!props.mirror
        ? [titleCmpt, previewCmpt]
        : [previewCmpt, titleCmpt]}
    </Grid>
  )
}

export default Block;
