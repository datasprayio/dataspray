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

import Head from 'next/head';
import {NextPageWithLayout} from "../../_app";
import Form from "@cloudscape-design/components/form";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";
import Header from "@cloudscape-design/components/header";
import FormField from "@cloudscape-design/components/form-field";
import Input from "@cloudscape-design/components/input";
import * as yup from "yup"
import Link from "@cloudscape-design/components/link";
import * as React from "react";
import {useState} from "react";
import {useRouterOnFollow} from "../../../common/util/useRouterOnFollow";
import Checkbox from "@cloudscape-design/components/checkbox";
import {CloudscapeFormik} from "../../../common/util/CloudscapeFormik";
import {useRouter} from "next/router";
import AuthLayout from "../../../common/dashboard/layout/AuthLayout";
import {useAuth} from "../../../common/dashboard/auth/auth";

const Page: NextPageWithLayout = () => {
    const router = useRouter();
    const routerOnFollow = useRouterOnFollow();
    const [error, setError] = useState<React.ReactNode>()
    const {signUp} = useAuth();

    return (
        <>
            <Head>
                <title>Sign up</title>
            </Head>
            <main>
                <CloudscapeFormik
                    initialValues={{
                        username: "",
                        email: "",
                        password: "",
                        tosAgree: false,
                        marketingAgree: false,
                    }}
                    validationSchema={(
                        yup.object().shape({
                            username: yup.string().required('Username is required.'),
                            email: yup.string().email('Email is not valid.').required('Email is required.'),
                            password: yup.string().required('Password is required.'),
                            tosAgree: yup.bool().isTrue('Please read and agree to continue.'),
                            marketingAgree: yup.bool().required('Please decide whether you want to receive marketing emails.'),
                        })
                    )}
                    onSubmit={(values) => signUp(values, setError, router.push)}
                >
                    {({
                        isSubmitting,
                        errors,
                        values,
                        setFieldValue,
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
                                        label="Email"
                                        errorText={errors?.email}
                                    >
                                        <Input
                                            type="text"
                                            name="email"
                                            placeholder="matus@example.com"
                                            onChangeNative={handleChange}
                                            onBlurNative={handleBlur}
                                            value={values?.email}
                                            autoFocus
                                        />
                                    </FormField>
                                    <FormField
                                        label="Username"
                                        errorText={errors?.username}
                                    >
                                        <Input
                                            type="text"
                                            name="username"
                                            placeholder="Matus"
                                            onChangeNative={handleChange}
                                            onBlurNative={handleBlur}
                                            value={values?.email}
                                            autoFocus
                                        />
                                    </FormField>
                                    <FormField
                                        label="Password"
                                        errorText={errors?.password}
                                        description={
                                            <>
                                                <Link
                                                    href="/dashboard/auth/forgot"
                                                    variant="primary"
                                                    fontSize="body-s"
                                                    onFollow={routerOnFollow}
                                                >
                                                    Forgot password?
                                                </Link>
                                                {" "}
                                                <Link
                                                    href="/dashboard/auth/signup"
                                                    variant="primary"
                                                    fontSize="body-s"
                                                    onFollow={routerOnFollow}
                                                >
                                                    Already have an account?
                                                </Link>
                                            </>
                                        }
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

                                    <FormField
                                        description={
                                            <>
                                                Please read and agree to our{" "}
                                                <Link
                                                    href="/terms-of-service"
                                                    external
                                                    variant="primary"
                                                    fontSize="body-s"
                                                >
                                                    terms of service
                                                </Link>
                                                {" "}and{" "}
                                                <Link
                                                    href="/privacy"
                                                    external
                                                    variant="primary"
                                                    fontSize="body-s"
                                                >
                                                    privacy policy
                                                </Link>
                                                .
                                            </>
                                        }
                                        label="Terms and conditions"
                                        errorText={errors?.tosAgree}
                                    >
                                        <Checkbox
                                            checked={values.tosAgree}
                                            onChange={e => setFieldValue("tosAgree", e.detail.checked)}
                                            onBlurNative={handleBlur}
                                        >
                                            I agree to the terms of service and privacy policy
                                        </Checkbox>
                                    </FormField>

                                    <FormField
                                        label="Marketing emails"
                                        errorText={errors?.marketingAgree}
                                    >
                                        <Checkbox
                                            checked={values.marketingAgree}
                                            onChange={e => setFieldValue("marketingAgree", e.detail.checked)}
                                            onBlurNative={handleBlur}
                                        >
                                            I want to receive marketing emails once in a while
                                        </Checkbox>
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
        pageTitle="Sign-up"
    >{page}</AuthLayout>
)

export default Page
