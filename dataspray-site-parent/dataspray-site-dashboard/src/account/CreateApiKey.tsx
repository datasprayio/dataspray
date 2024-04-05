import {ContentLayout, DatePicker, MultiselectProps, Tiles} from "@cloudscape-design/components";
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
import Checkbox from "@cloudscape-design/components/checkbox";
import {TargetSelect} from "./TargetSelect";
import Option = MultiselectProps.Option;

export const CreateApiKey = () => {
    const {currentOrganizationName} = useAuth();
    const [error, setError] = useState<React.ReactNode>();
    const [selectedTargets, setSelectedTargets] = useState<Option[]>([])

    return (
            <ContentLayout header={(
                    <Header
                            variant="h1"
                            description="You can create an Access Key to use the DataSpray API programmatically."
                    >
                        Create distribution
                    </Header>
            )}>
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
                                    hasExpiry: yup.boolean(),
                                    expiresAt: yup.date().when('hasExpiry', hasExpiry2 => hasExpiry2
                                            ? yup.date().min(new Date(), 'Expiry date must be in the future.').required('Provide an expiry date.')
                                            : yup.date().notRequired()),
                                    queueWhitelist: yup.array().of(yup.string().required('Provide a target name.')).when('type',
                                            (type: any) => (type === 'user')
                                                    ? yup.array().length(0, 'User access keys cannot have a target whitelist.')
                                                    : yup.array().min(1, 'Provide at least one target name.'))
                                })
                        )}
                        onSubmit={async (values) => {
                            try {
                                await getClient().authNz().createApiKey({
                                    apiKeyCreate: {
                                        organizationName: currentOrganizationName!,
                                        description: values.description,
                                        expiresAt: values.hasExpiry ? values.expiresAt.getTime() : undefined,
                                        queueWhitelist: values.type === 'system' ? values.queueWhitelist : undefined,
                                    }
                                });
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
                                        header={<Header variant="h1">Create Access Key</Header>}
                                        errorText={error}
                                >
                                    <SpaceBetween direction="vertical" size="l">
                                        <Container header={<Header variant="h2">Primary usage</Header>}>
                                            <FormField label="Primary usage" stretch>
                                                <Tiles items={[
                                                    {
                                                        value: 'system',
                                                        label: 'System',
                                                        description: 'Access to specific ingestion targets only. Useful for system integrations.',
                                                    },
                                                    {
                                                        value: 'user',
                                                        label: 'User',
                                                        description: 'Full access to all API endpoints to an organization.',
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
                                        </Container>
                                        <Container header={<Header variant="h2">Organization</Header>}>
                                            <FormField
                                                    label="Scope of access"
                                                    errorText={errors?.type}
                                            >
                                                <Input
                                                        type="text"
                                                        name="organizationName"
                                                        readOnly
                                                        placeholder="Organization name"
                                                        onChangeNative={handleChange}
                                                        onBlurNative={handleBlur}
                                                        value={currentOrganizationName || ''}
                                                />
                                            </FormField>
                                        </Container>
                                        <Container header={<Header variant="h2">Expiration</Header>}>
                                            <SpaceBetween size="m">
                                                <FormField
                                                        label="Configure expiration"
                                                        errorText={errors.hasExpiry}
                                                >
                                                    <Checkbox
                                                            checked={!!values.hasExpiry}
                                                            onChange={({detail: {checked}}) =>
                                                                    handleChange({
                                                                        target: {
                                                                            name: 'hasExpiry',
                                                                            value: checked
                                                                        }
                                                                    })}>
                                                        {values.hasExpiry ? 'Will expire' : 'Never expires'}
                                                    </Checkbox>
                                                </FormField>
                                                <FormField
                                                        label="Expiry"
                                                        errorText={errors.expiresAt ? `${errors.expiresAt}` : undefined}
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
                                        <Container header={<Header variant="h2">Organization</Header>}>
                                            <FormField
                                                    label="Scope of access"
                                                    errorText={errors?.type}
                                            >
                                                <TargetSelect
                                                        currentOrganizationName={currentOrganizationName}
                                                        selectedTargets={selectedTargets}
                                                        setSelectedTargets={setSelectedTargets}/>
                                            </FormField>
                                        </Container>
                                    </SpaceBetween>
                                </Form>
                            </form>
                    )}
                </CloudscapeFormik>
            </ContentLayout>
    );
};

