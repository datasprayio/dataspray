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

import {DatePicker, MultiselectProps, RadioGroup, Tiles} from "@cloudscape-design/components";
import {useAuth} from "../auth/auth";
import React, {useState} from "react";
import Header from "@cloudscape-design/components/header";
import Form from "@cloudscape-design/components/form";
import {CloudscapeFormik} from "../util/CloudscapeFormik";
import * as yup from "yup";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";
import FormField from "@cloudscape-design/components/form-field";
import Input from "@cloudscape-design/components/input";
import {getClient} from "../util/dataSprayClientWrapper";
import Container from "@cloudscape-design/components/container";
import {dateToYyyyMmDd, yyyyMmDdToDate} from "../util/dateUtil";
import {TargetSelect} from "./TargetSelect";
import {ApiKeyWithSecret} from "dataspray-client";
import Option = MultiselectProps.Option;

export const CreateApiKey = (props: {
    onCreated: (apiKey: ApiKeyWithSecret) => void
}) => {
    const {currentOrganizationName} = useAuth();
    const [error, setError] = useState<React.ReactNode>();
    const [selectedTopics, setSelectedTopics] = useState<Option[]>([])

    return (
            <CloudscapeFormik
                    initialValues={{
                        type: 'system',
                        description: '',
                        hasExpiry: true,
                        expiresAt: new Date(new Date().getTime() + (30 * 24 * 60 * 60 * 1000)),
                        queueWhitelist: [],
                    }}
                    validationSchema={(
                            yup.object().shape({
                                type: yup.string().oneOf(['user', 'system'], 'Select a type.').required('Select a type.'),
                                description: yup.string().required('Provide a description for the access key.'),
                                hasExpiry: yup.boolean().required(),
                                expiresAt: yup.date().when('hasExpiry', (hasExpiry2, schema) => hasExpiry2[0]
                                        ? yup.date().min(new Date(), 'Expiry date must be in the future.').required('Provide an expiry date.').required()
                                        : schema),
                                queueWhitelist: yup.array().when('type',
                                        (type, schema) => (type[0] === 'user')
                                                ? schema
                                                : yup.array().of(yup.string().required('Provide a target name.')).min(1, 'Provide at least one target name.'))
                            })
                    )}
                    onSubmit={async (values) => {
                        try {
                            const result = await getClient().authNz().createApiKey({
                                organizationName: currentOrganizationName!,
                                apiKeyCreate: {
                                    description: values.description,
                                    expiresAt: values.hasExpiry ? values.expiresAt.getTime() : undefined,
                                    queueWhitelist: values.type === 'system' ? values.queueWhitelist : undefined,
                                }
                            });
                            props.onCreated(result);
                        } catch (e: any) {
                            console.error('Failed to create access token', e ?? 'Unknown error')
                            setError(e?.message || ('Failed to create access token: ' + (e || 'Unknown error')))
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
                                    variant="full-page"
                                    actions={(
                                            <SpaceBetween direction="horizontal" size="xs">
                                                <Button variant="link" href='/account'>Cancel</Button>
                                                <Button disabled={isSubmitting} variant="primary"
                                                        onClick={e => handleSubmit()}>Submit</Button>
                                            </SpaceBetween>
                                    )}
                                    errorText={error}
                            >
                                <SpaceBetween direction="vertical" size="l">
                                    <Container header={<Header variant="h2">Access Key</Header>}>
                                        <SpaceBetween size="m">
                                            <FormField stretch>
                                                <Tiles items={[
                                                    {
                                                        value: 'system',
                                                        label: 'System',
                                                        description: 'Access to specific ingestion topics only. Useful for system integrations.',
                                                    },
                                                    {
                                                        value: 'user',
                                                        label: 'User',
                                                        description: 'Full access to all API endpoints in an organization.',
                                                    },
                                                ]}
                                                       value={values.type}
                                                       onChange={({detail: {value}}) => handleChange({
                                                           target: {
                                                               name: 'type',
                                                               value: value
                                                           }
                                                       })}
                                                />
                                            </FormField>
                                            <FormField
                                                    label="Description"
                                                    errorText={errors.description}
                                            >
                                                <Input
                                                        type="text"
                                                        name="description"
                                                        onChangeNative={handleChange}
                                                        onBlurNative={handleBlur}
                                                        value={values.description}
                                                />
                                            </FormField>
                                        </SpaceBetween>
                                    </Container>
                                    <Container variant='stacked' header={<Header variant="h2">Scope of access</Header>}>
                                        <SpaceBetween size="m">
                                            <FormField
                                                    label="Organization name to grant access to"
                                                    errorText={errors?.type}
                                            >
                                                <Input
                                                        type="text"
                                                        name="organizationName"
                                                        disabled
                                                        placeholder="Organization name"
                                                        onChangeNative={handleChange}
                                                        onBlurNative={handleBlur}
                                                        value={currentOrganizationName || ''}
                                                />
                                            </FormField>
                                            <FormField
                                                    label="List of topics to grant access to"
                                                    errorText={errors?.queueWhitelist}
                                            >
                                                <TargetSelect
                                                        currentOrganizationName={currentOrganizationName}
                                                        setSelectedTopics={setSelectedTopics}
                                                        {...(values.type === 'user' ? {
                                                            // Options when all topics are allowed
                                                            disabled: true,
                                                            placeholder: 'All topics granted',
                                                            selectedTopics: [],
                                                        } : {
                                                            selectedTopics,
                                                        })}
                                                />
                                            </FormField>
                                        </SpaceBetween>
                                    </Container>
                                    <Container variant='stacked' header={<Header variant="h2">Expiration</Header>}>
                                        <SpaceBetween size="m">
                                            <FormField
                                                    errorText={errors.hasExpiry}
                                            >
                                                <RadioGroup
                                                        onChange={({detail: {value}}) => handleChange({
                                                            target: {
                                                                name: 'hasExpiry',
                                                                value: value === "true",
                                                            }
                                                        })}
                                                        value={values.hasExpiry ? "true" : "false"}
                                                        items={[
                                                            {value: "false", label: "Never expires"},
                                                            {value: "true", label: "Expire at specified date"},
                                                        ]}
                                                />
                                            </FormField>
                                            <FormField
                                                    errorText={(errors.expiresAt) ? `${errors.expiresAt}` : undefined}
                                            >
                                                <DatePicker
                                                        disabled={!values.hasExpiry}
                                                        previousMonthAriaLabel="Previous month"
                                                        nextMonthAriaLabel="Next month"
                                                        todayAriaLabel="Today"
                                                        value={dateToYyyyMmDd(values.expiresAt)}
                                                        onChange={({detail: {value}}) => handleChange({
                                                            target: {
                                                                name: 'expiresAt',
                                                                value: yyyyMmDdToDate(value)
                                                            }
                                                        })}
                                                        openCalendarAriaLabel={selectedDate =>
                                                                'Choose expiry date' + (selectedDate ? `, selected date is ${selectedDate}` : '')
                                                        }
                                                />
                                            </FormField>
                                        </SpaceBetween>
                                    </Container>
                                </SpaceBetween>
                            </Form>
                        </form>
                )}
            </CloudscapeFormik>
    );
};

