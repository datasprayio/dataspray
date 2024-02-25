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
import DashboardLayout from "../../layout/DashboardLayout";
import DashboardAppLayout from "../../layout/DashboardAppLayout";
import {useAuth} from "../../auth/auth";
import {ContentLayout} from "@cloudscape-design/components";
import Form from "@cloudscape-design/components/form";
import {CloudscapeFormik} from "../../util/CloudscapeFormik";
import * as yup from "yup";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";
import Header from "@cloudscape-design/components/header";
import FormField from "@cloudscape-design/components/form-field";
import Input from "@cloudscape-design/components/input";
import * as React from "react";
import {useState} from "react";

const Page: NextPageWithLayout = () => {
    const {idToken} = useAuth();
    const [error, setError] = useState<React.ReactNode>();

    return (
        <DashboardAppLayout
            content={(
                <>
                    <ContentLayout header={(
                        <Header
                            variant="h1"
                            description="This is a generic description used in the header."
                        >Header</Header>
                    )}>
                        <CloudscapeFormik
                            initialValues={{
                                name: '',
                            }}
                            validationSchema={(
                                yup.object().shape({
                                    name: yup.string().required('Organization name is required.'),
                                })
                            )}
                            onSubmit={(values) => {
                                // TODO use setError
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
                                                        onClick={e => handleSubmit()}>Submit</Button>
                                            </SpaceBetween>
                                        }
                                        header={<Header variant="h1">Form header</Header>}
                                        errorText={error}
                                    >
                                        <SpaceBetween direction="vertical" size="l">
                                            <FormField
                                                label='Organization name'
                                                errorText={errors?.name}
                                                description='Will be required for API calls.'
                                            >
                                                <Input
                                                    type="text"
                                                    name="organizationName"
                                                    onChangeNative={handleChange}
                                                    onBlurNative={handleBlur}
                                                    value={values?.name}
                                                    autoFocus
                                                />
                                            </FormField>
                                        </SpaceBetween>
                                    </Form>
                                </form>
                            )}
                        </CloudscapeFormik>
                    </ContentLayout>
                </>
            )}
        />
    )
}

Page.getLayout = (page) => (
    <DashboardLayout
        pageTitle='Home'
    >{page}</DashboardLayout>
)

export default Page
