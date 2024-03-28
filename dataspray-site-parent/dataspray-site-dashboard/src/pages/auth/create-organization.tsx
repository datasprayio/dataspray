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
import {NextPageWithLayout} from "../_app";
import Form from "@cloudscape-design/components/form";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";
import Header from "@cloudscape-design/components/header";
import FormField from "@cloudscape-design/components/form-field";
import Input from "@cloudscape-design/components/input";
import * as yup from "yup"
import * as React from "react";
import {useState} from "react";
import {CloudscapeFormik} from "../../util/CloudscapeFormik";
import {useRouter} from "next/router";
import AuthLayout from "../../layout/AuthLayout";
import {useAuth} from "../../auth/auth";
import {getClient} from "../../util/dataSprayClientWrapper";

const Page: NextPageWithLayout = () => {
    const router = useRouter();
    const {signInRefresh, authResult} = useAuth('redirect-if-signed-out');
    const [error, setError] = useState<React.ReactNode>()

    return (
            <>
                <Head>
                    <title>Create Organization</title>
                </Head>
                <main>
                    <CloudscapeFormik
                            initialValues={{
                                organizationName: "",
                            }}
                            validationSchema={(
                                    yup.object().shape({
                                        organizationName: yup.string().trim().required('Organization name is required.'),
                                    })
                            )}
                            onSubmit={async (values) => {
                                try {
                                    await getClient().organization().createOrganization({
                                        organizationName: values.organizationName,
                                    })
                                } catch (e: any) {
                                    if (e.status === 409) {
                                        setError('Organization name already exists.');
                                    } else {
                                        setError(e);
                                    }
                                    return;
                                }
                                try {
                                    await signInRefresh(authResult!.refreshToken, '/', setError, router.push);
                                } catch (e: any) {
                                    setError('Failed to load organization, please re-login.');
                                    return;
                                }
                            }}
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
                                                            onClick={e => handleSubmit()}>Create</Button>
                                                </SpaceBetween>
                                            }
                                            header={<Header variant="h1">Create organization</Header>}
                                            errorText={error}
                                    >
                                        <SpaceBetween direction="vertical" size="l">
                                            <FormField
                                                    label="Name"
                                                    errorText={errors?.organizationName}
                                            >
                                                <Input
                                                        type="text"
                                                        name="organizationName"
                                                        placeholder="my-company"
                                                        onChangeNative={handleChange}
                                                        onBlurNative={handleBlur}
                                                        value={values?.organizationName || ''}
                                                        autoFocus
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
                pageTitle="Create Organization"
        >{page}</AuthLayout>
)

export default Page
