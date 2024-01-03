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

import {NextPageWithLayout} from "../../_app";
import Form from "@cloudscape-design/components/form";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";
import Header from "@cloudscape-design/components/header";
import FormField from "@cloudscape-design/components/form-field";
import Input from "@cloudscape-design/components/input";
import * as yup from "yup"
import * as React from "react";
import {useState} from "react";
import {useRouterOnFollow} from "../../../common/util/useRouterOnFollow";
import {CloudscapeFormik} from "../../../common/util/CloudscapeFormik";
import {useRouter} from "next/router";
import AuthLayout from "../../../common/dashboard/layout/AuthLayout";
import {useAuth} from "../../../common/dashboard/auth/auth";

const Page: NextPageWithLayout = () => {
    const router = useRouter();
    const routerOnFollow = useRouterOnFollow();
    const [error, setError] = useState<React.ReactNode>();
    const username = router.query.username?.toString() || '';
    const session = router.query.session?.toString() || '';
    const to = router.query.to?.toString();
    const {signInPasswordChange} = useAuth();

    return (
        <>
            <main>
                <CloudscapeFormik
                    initialValues={{
                        password: '',
                    }}
                    validationSchema={(
                        yup.object().shape({
                            password: yup.string().required('Password is required.'),
                        })
                    )}
                    onSubmit={(values) => signInPasswordChange(username, session, values.password, to, setError, router.push)}
                >
                    {({
                        isSubmitting,
                        errors,
                        values,
                        handleChange,
                        handleBlur,
                        handleSubmit,
                    }) => (

                        <form onSubmit={handleSubmit}>
                            <Form
                                variant="embedded"
                                actions={
                                    <SpaceBetween direction="horizontal" size="xs">
                                        <Button disabled={isSubmitting} variant="primary"
                                                onClick={e => handleSubmit()}>Submit</Button>
                                    </SpaceBetween>
                                }
                                header={<Header variant="h1">Form header</Header>}
                                errorText={error}
                            >
                                <SpaceBetween direction="vertical" size="l">
                                    <FormField
                                        label="Password"
                                        errorText={errors?.password}
                                    >
                                        <Input
                                            type="password"
                                            name="password"
                                            placeholder="Password"
                                            onChangeNative={handleChange}
                                            onBlurNative={handleBlur}
                                            value={values?.password}
                                        />
                                    </FormField>
                                </SpaceBetween>
                            </Form>
                        </form>
                    )}
                </CloudscapeFormik>
            </main>
        </>
    )
}

Page.getLayout = (page) => (
    <AuthLayout
        pageTitle="Change password"
    >{page}</AuthLayout>
)

export default Page
