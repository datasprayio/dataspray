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

const Page: NextPageWithLayout = () => {
    const router = useRouter();
    const [error, setError] = useState<React.ReactNode>();
    const username = router.query.username?.toString() || '';
    const to = router.query.to?.toString();
    const session = router.query.session?.toString() || '';
    const {signInConfirmTotp} = useAuth();

    return (
            <>
                <main>
                    <CloudscapeFormik
                            initialValues={{
                                code: '',
                            }}
                            validationSchema={(
                                    yup.object().shape({
                                        code: yup.number().required('Provide a code from your two-factor device.'),
                                    })
                            )}
                            onSubmit={(values) => signInConfirmTotp(username, session, values.code, to, setError, router.push)}
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
                                            header={<Header variant="h1">Confirm TOTP</Header>}
                                            errorText={error}
                                    >
                                        <SpaceBetween direction="vertical" size="l">
                                            <FormField
                                                    label="Confirmation code"
                                                    errorText={errors?.code}
                                            >
                                                <Input
                                                        type="text"
                                                        name="code"
                                                        placeholder="123456"
                                                        onChangeNative={handleChange}
                                                        onBlurNative={handleBlur}
                                                        value={values?.code}
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
                pageTitle="Two-factor authentication"
        >{page}</AuthLayout>
)

export default Page
