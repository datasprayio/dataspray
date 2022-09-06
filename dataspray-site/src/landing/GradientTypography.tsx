import { Typography, useTheme } from "@mui/material";
import React from "react";

const GradientTypography = (props: React.ComponentProps<typeof Typography>) => {
  const theme = useTheme();

  var from, to;
  switch (props.color) {
    default:
    case 'primary':
      from = theme.palette.primary.light;
      to = theme.palette.primary.dark;
      break;
    case 'secondary':
      from = theme.palette.secondary.light;
      to = theme.palette.secondary.dark;
      break;
  }

  return (
    <Typography
      component='span'
      {...props}
      sx={{
        ...props.sx,
        background: `linear-gradient(to right, ${from}, ${to})`,
        backgroundClip: 'text',
        WebkitTextFillColor: 'transparent',
      }}
    />
  )
}

export default GradientTypography;
