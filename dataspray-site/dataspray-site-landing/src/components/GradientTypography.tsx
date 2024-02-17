/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import {Typography, TypographyTypeMap, useTheme} from "@mui/material";
import {OverridableComponent} from "@mui/material/OverridableComponent";
import React from "react";

const GradientTypography: OverridableComponent<TypographyTypeMap> = (props: React.ComponentPropsWithoutRef<typeof Typography>) => {
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
                '-webkit-background-clip': 'text',
                WebkitTextFillColor: 'transparent',
            }}
        />
    )
}

export default GradientTypography;