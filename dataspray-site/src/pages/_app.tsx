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

import type {AppProps} from 'next/app';
import Head from 'next/head';
import React, {ReactElement, ReactNode} from "react";
import {NextPage} from "next";
import '@cloudscape-design/global-styles/index.css';
import {applyDefaultMode} from "../common/util/modeUtil";

// Setup dark/light mode
applyDefaultMode();

export type NextPageWithLayout<P = {}, IP = P> = NextPage<P, IP> & {
    getLayout?: (page: ReactElement) => ReactNode
}

type AppPropsWithLayout = AppProps & {
    Component: NextPageWithLayout
}

export default function MyApp({Component, pageProps}: AppPropsWithLayout) {

    // Render component
    let component: ReactNode = (<Component {...pageProps} />);

    // Use the layout defined at the page level, if available
    const getLayout = Component.getLayout || undefined
    if (getLayout) {
        component = getLayout(component)
    }

    return (
        <>
            <Head>
                <meta name="viewport" content="initial-scale=1, width=device-width"/>
                <link rel="icon" href="/favicon.ico" sizes="any"/>
                <link rel="icon" href="/favicon.svg" type="image/svg+xml"/>
            </Head>
            {component}
        </>
    );
}
