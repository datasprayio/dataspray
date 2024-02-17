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

import {useAuth} from "../../auth/auth";
import * as React from "react";
import {useEffect} from "react";
import {useRouter} from "next/router";
import {NextPageWithLayout} from "../_app";
import AuthLayout from "../../layout/AuthLayout";

const Page: NextPageWithLayout = () => {
    const {signOut} = useAuth()
    const router = useRouter();

    useEffect(() => {

        // Sign out and redirect to sign in page
        signOut()
        router.push('/auth/signin');

        // Run only once
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Nothing to display
    return null;
}

Page.getLayout = (page) => (
    <AuthLayout
        pageTitle="Signing out..."
    >{page}</AuthLayout>
)

export default Page
