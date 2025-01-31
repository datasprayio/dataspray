/*
 * Copyright 2025 Matus Faro
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

import React from 'react';
import {
    AttributeEditor,
    ColumnLayout,
    Container,
    Header,
    Input,
    Select,
    SpaceBetween
} from '@cloudscape-design/components';
import {CloudscapeFormik} from "../util/CloudscapeFormik";
import * as yup from "yup";
import Form from "@cloudscape-design/components/form";
import FormField from "@cloudscape-design/components/form-field";
import {Topic, Topics, TopicStoreKey, TopicStoreKeyTableTypeEnum, TopicStream} from "dataspray-client";
import Checkbox from "@cloudscape-design/components/checkbox";
import {useAlerts} from "../util/useAlerts";
import {getClient} from "../util/dataSprayClientWrapper";
import {useAuth} from "../auth/auth";
import Button from "@cloudscape-design/components/button";

/** Matches DEFAULT_BATCH_RETENTION in TopicStore.java */
export const DEFAULT_BATCH_RETENTION = 'THREE_MONTHS';
/** Matches BatchRetention in TopicStore.java */
export const BATCH_RETENTION = new Map([
    ['DAY', {value: 'DAY', inDays: 1, label: 'Day'}],
    ['WEEK', {value: 'WEEK', inDays: 7, label: 'Week'}],
    ['THREE_MONTHS', {value: 'THREE_MONTHS', inDays: 3 * 30, label: '3 Months'}],
    ['YEAR', {value: 'YEAR', inDays: 366, label: 'Year'}],
    ['THREE_YEARS', {value: 'THREE_YEARS', inDays: 3 * 366, label: '3 Years'}],
]);
const BATCH_RETENTION_VALUES = Array.from(BATCH_RETENTION, ([, val]) => val);
const TABLE_TYPE_VALUES = [
    {label: 'Primary', value: TopicStoreKeyTableTypeEnum.Primary},
    {label: 'GSI', value: TopicStoreKeyTableTypeEnum.Gsi},
    {label: 'LSI', value: TopicStoreKeyTableTypeEnum.Lsi},
];

export enum EditType {
    EDIT_TOPIC,
    EDIT_DEFAULT_TOPIC,
    CREATE_TOPIC,
}

export function EditTopic(props: {
    organizationName?: string;
    topicName?: string; // name to edit (Only supply for EditType.EDIT_TOPIC)
    editType: EditType;
    topic?: Topic;
    onUpdated?: (topics: Topics) => void;
    allowUndefinedTopics?: boolean;
}) {
    const {currentOrganizationName} = useAuth();
    const {beginProcessing} = useAlerts();
    return (
        <CloudscapeFormik<{
            allowUndefinedTopics: boolean;
            topicName: string;
            batchEnabled: boolean;
            batchRetention: string;
            storeEnabled: boolean;
            storeTtlInSec: number;
            storeKeys: TopicStoreKey[];
            storeBlacklist?: string[];
            storeWhitelist?: string[];
            streams: TopicStream[];
        }>
            initialValues={{
                allowUndefinedTopics: !!props.allowUndefinedTopics,
                topicName: props.topicName || '',
                batchEnabled: true,
                batchRetention: DEFAULT_BATCH_RETENTION,
                storeEnabled: false,
                storeTtlInSec: 24 * 60 * 60,
                storeKeys: [],
                streams: [],
            }}
            validationSchema={(
                yup.object().shape({
                    topicName: yup.string().required(),
                    batchEnabled: yup.boolean().required(),
                    batchRetention: yup.string().required(),
                    storeEnabled: yup.boolean().required(),
                    storeTtlInSec: yup.number(),
                    storeKeys: yup.array().required(),
                    storeBlacklist: yup.array(),
                    storeWhitelist: yup.array(),
                    streams: yup.array(),
                })
            )}
            onSubmit={async (values) => {
                if (!currentOrganizationName) {
                    return;
                }

                // Convert formik state to API Topic model
                const topic: Topic = {
                    batch: !values.batchEnabled ? undefined : {
                        retentionInDays: BATCH_RETENTION.get(values.batchRetention)!.inDays,
                    },
                    streams: !values.streams?.length ? undefined : values.streams,
                    store: !values.storeEnabled ? undefined : {
                        keys: values.storeKeys,
                        ttlInSec: values.storeTtlInSec,
                        blacklist: !values.storeBlacklist?.length ? undefined : values.storeBlacklist,
                        whitelist: !values.storeWhitelist?.length ? undefined : values.storeWhitelist,
                    },
                };

                var messageProcessing;
                var messageSuccess;
                var messageFailure;
                switch (props.editType) {
                    case EditType.CREATE_TOPIC:
                        messageProcessing = `Creating topic ${values.topicName}`;
                        messageSuccess = `Created topic ${values.topicName} successfully`;
                        messageFailure = `Failed to create topic ${values.topicName}`;
                        break;
                    case EditType.EDIT_TOPIC:
                        messageProcessing = `Saving topic ${props.topicName}`;
                        messageSuccess = `Saved topic ${props.topicName} successfully`;
                        messageFailure = `Failed to save topic ${props.topicName}`;
                        break;
                    case EditType.EDIT_DEFAULT_TOPIC:
                        if (values.allowUndefinedTopics) {
                            messageProcessing = 'Disabling default topic';
                            messageSuccess = 'Disabled default topic successfully';
                            messageFailure = 'Failed to disable default topic';
                        } else {
                            messageProcessing = 'Updating default topic';
                            messageSuccess = 'Updated default topic successfully';
                            messageFailure = 'Failed to update default topic';
                        }
                        break;
                }
                const {onSuccess, onError} = beginProcessing({content: messageProcessing});
                try {
                    if (props.editType === EditType.EDIT_DEFAULT_TOPIC) {
                        await getClient().control().updateDefaultTopic({
                            organizationName: currentOrganizationName,
                            updateDefaultTopicRequest: {
                                allowUndefined: values.allowUndefinedTopics,
                                topic,
                            }
                        })
                    } else {
                        await getClient().control().updateTopic({
                            organizationName: currentOrganizationName,
                            topicName: props.editType === EditType.EDIT_TOPIC
                                ? props.topicName! : values.topicName,
                            topic,
                        })
                    }
                    onSuccess({content: messageSuccess});
                } catch (e: any) {
                    onError({content: `${messageFailure}: ${e?.message || 'Unknown error'}`});
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
                setFieldValue,
            }) => {
                const editingDisabled = props.editType === EditType.EDIT_DEFAULT_TOPIC && !values.allowUndefinedTopics
                return (
                    <form onSubmit={handleSubmit}>
                        <Form
                            header={<Header variant="h1">{!!props.topicName ? 'Edit' : 'Create'} topic</Header>}
                            actions={
                                <SpaceBetween direction="horizontal" size="xs" alignItems='center'>
                                    <Button disabled={isSubmitting} variant="primary"
                                            onClick={e => handleSubmit()}>Submit</Button>
                                </SpaceBetween>
                            }
                        >
                            <SpaceBetween direction="vertical" size="l">
                                {props.editType === EditType.EDIT_DEFAULT_TOPIC ? (
                                    <FormField label='Enabled'>
                                        <Checkbox
                                            name='allowUndefinedTopics'
                                            checked={values.allowUndefinedTopics}
                                            onChange={e => setFieldValue("allowUndefinedTopics", e.detail.checked)}
                                            onBlurNative={handleBlur}
                                        >
                                            Allow data for arbitrary undefined topic to be consumed usign this
                                            default
                                        </Checkbox>
                                    </FormField>
                                ) : (
                                    <FormField
                                        label="Topic name"
                                        errorText={errors?.topicName}
                                    >
                                        <Input
                                            placeholder='name'
                                            disabled={!!props.topicName}
                                            value={values.topicName}
                                            onChange={e => setFieldValue("topicName", e.detail.value)}
                                        />
                                    </FormField>
                                )}
                                <Container header={<Header variant="h2">Send to Data Lake</Header>}>
                                    <SpaceBetween size="m">
                                        <FormField
                                            errorText={errors?.batchEnabled}
                                        >
                                            <Checkbox
                                                disabled={editingDisabled}
                                                checked={values.batchEnabled}
                                                onChange={e => setFieldValue("batchEnabled", e.detail.checked)}
                                                onBlurNative={handleBlur}
                                            >
                                                Enable storing data for batch processing
                                            </Checkbox>
                                        </FormField>

                                        <FormField
                                            label="Retention"
                                            errorText={errors?.batchRetention}
                                        >
                                            <Select
                                                disabled={editingDisabled || !values.batchEnabled}
                                                selectedOption={BATCH_RETENTION.get(values.batchRetention)!}
                                                onChange={e => setFieldValue("batchRetention", e.detail.selectedOption?.value)}
                                                options={BATCH_RETENTION_VALUES}
                                            />
                                        </FormField>
                                    </SpaceBetween>
                                </Container>
                                <Container header={<Header variant="h2">Send to Stream processing</Header>}>
                                    <SpaceBetween size="m">
                                        <AttributeEditor
                                            onAddButtonClick={() => setFieldValue("streams", [
                                                ...values.streams,
                                                {
                                                    name: '',
                                                },
                                            ])}
                                            onRemoveButtonClick={({
                                                detail: {itemIndex}
                                            }) => {
                                                const tmpItems = [...values.streams];
                                                tmpItems.splice(itemIndex, 1);
                                                setFieldValue("streams", tmpItems)
                                            }}
                                            items={values.streams}
                                            removeButtonText="Remove stream"
                                            addButtonText="Add new stream"
                                            disableAddButton={editingDisabled}
                                            isItemRemovable={() => !editingDisabled}
                                            definition={[
                                                {
                                                    label: "Stream name",
                                                    control: (item, index) => (
                                                        <Input
                                                            disabled={editingDisabled}
                                                            placeholder='name'
                                                            value={item.name}
                                                            onChange={e => {
                                                                const tmpItems = [...values.streams];
                                                                tmpItems[index] = {
                                                                    ...tmpItems[index],
                                                                    name: e.detail.value
                                                                };
                                                                setFieldValue("streams", tmpItems)
                                                            }}
                                                        />
                                                    )
                                                },
                                            ]}
                                            empty="No streams defined."
                                        />
                                    </SpaceBetween>
                                </Container>
                                <Container header={<Header variant="h2">Store as state</Header>}>
                                    <SpaceBetween size="m">
                                        <FormField
                                            errorText={errors?.storeEnabled}
                                        >
                                            <Checkbox
                                                name='storeEnabled'
                                                disabled={editingDisabled}
                                                checked={values.storeEnabled}
                                                onChange={e => setFieldValue("storeEnabled", e.detail.checked)}
                                                onBlurNative={handleBlur}
                                            >
                                                Enable storing data in Dynamo
                                            </Checkbox>
                                        </FormField>
                                        <ColumnLayout columns={3}>
                                            <FormField
                                                label="Time To Live (seconds)"
                                                errorText={errors?.storeTtlInSec}
                                            >
                                                <Input
                                                    type="number"
                                                    name="storeTtlInSec"
                                                    disabled={editingDisabled || !values.storeEnabled}
                                                    onChangeNative={handleChange}
                                                    onBlurNative={handleBlur}
                                                    value={String(values.storeTtlInSec)}
                                                />
                                            </FormField>
                                            <FormField
                                                label="Event key whitelist"
                                                errorText={errors?.storeWhitelist}
                                            >
                                                <Input
                                                    type="number"
                                                    placeholder='key1,key2,...'
                                                    disabled={editingDisabled || !values.storeEnabled}
                                                    value={(values.storeWhitelist || []).join(',')}
                                                    onChange={e => setFieldValue("storeWhitelist", e.detail.value.split(','))}
                                                />
                                            </FormField>
                                            <FormField
                                                label="Event key blacklist"
                                                errorText={errors?.storeBlacklist}
                                            >
                                                <Input
                                                    type="number"
                                                    placeholder='key1,key2,...'
                                                    disabled={editingDisabled || !values.storeEnabled}
                                                    value={(values.storeBlacklist || []).join(',')}
                                                    onChange={e => setFieldValue("storeBlacklist", e.detail.value.split(','))}
                                                />
                                            </FormField>
                                        </ColumnLayout>
                                        <AttributeEditor
                                            onAddButtonClick={() => setFieldValue("storeKeys", [
                                                ...values.storeKeys,
                                                {
                                                    tableType: TopicStoreKeyTableTypeEnum.Primary,
                                                    indexNumber: 0,
                                                    pkParts: [],
                                                    skParts: [],
                                                    rangePrefix: '',
                                                },
                                            ])}
                                            onRemoveButtonClick={({
                                                detail: {itemIndex}
                                            }) => {
                                                const tmpItems = [...values.storeKeys];
                                                tmpItems.splice(itemIndex, 1);
                                                setFieldValue("storeKeys", tmpItems)
                                            }}
                                            items={values.storeKeys}
                                            removeButtonText="Remove key"
                                            addButtonText="Add new key"
                                            disableAddButton={editingDisabled || !values.storeEnabled}
                                            isItemRemovable={() => !(editingDisabled || !values.storeEnabled)}
                                            definition={[
                                                {
                                                    label: "Type",
                                                    control: (item, index) => (
                                                        <Select
                                                            disabled={editingDisabled || !values.storeEnabled}
                                                            selectedOption={TABLE_TYPE_VALUES.find(o => o.value === item.tableType)!}
                                                            onChange={e => {
                                                                const tmpItems = [...values.storeKeys];
                                                                tmpItems[index] = {
                                                                    ...tmpItems[index],
                                                                    tableType: e.detail.selectedOption.value as TopicStoreKeyTableTypeEnum
                                                                };
                                                                setFieldValue("storeKeys", tmpItems)
                                                            }}
                                                            options={TABLE_TYPE_VALUES}
                                                        />
                                                    )
                                                },
                                                {
                                                    label: "Index number",
                                                    control: (item, index) => (
                                                        <Input
                                                            disabled={editingDisabled || !values.storeEnabled}
                                                            type="number"
                                                            value={String(item.indexNumber)}
                                                            onChange={e => {
                                                                const tmpItems = [...values.storeKeys];
                                                                tmpItems[index] = {
                                                                    ...tmpItems[index],
                                                                    indexNumber: parseInt(e.detail.value)
                                                                };
                                                                setFieldValue("storeKeys", tmpItems)
                                                            }}
                                                        />
                                                    )
                                                },
                                                {
                                                    label: "Primary Key parts",
                                                    control: (item, index) => (
                                                        <Input
                                                            disabled={editingDisabled || !values.storeEnabled}
                                                            placeholder='key1,key2,...'
                                                            value={(item.pkParts || []).join(',')}
                                                            onChange={e => {
                                                                const tmpItems = [...values.storeKeys];
                                                                tmpItems[index] = {
                                                                    ...tmpItems[index],
                                                                    pkParts: e.detail.value.split(',')
                                                                };
                                                                setFieldValue("storeKeys", tmpItems)
                                                            }}
                                                        />
                                                    )
                                                },
                                                {
                                                    label: "Range Key parts",
                                                    control: (item, index) => (
                                                        <Input
                                                            disabled={editingDisabled || !values.storeEnabled}
                                                            placeholder='key1,key2,...'
                                                            value={(item.skParts || []).join(',')}
                                                            onChange={e => {
                                                                const tmpItems = [...values.storeKeys];
                                                                tmpItems[index] = {
                                                                    ...tmpItems[index],
                                                                    skParts: e.detail.value.split(',')
                                                                };
                                                                setFieldValue("storeKeys", tmpItems)
                                                            }}
                                                        />
                                                    )
                                                },
                                                {
                                                    label: "Range prefix",
                                                    control: (item, index) => (
                                                        <Input
                                                            disabled={editingDisabled || !values.storeEnabled}
                                                            placeholder='prefix'
                                                            value={item.rangePrefix}
                                                            onChange={e => {
                                                                const tmpItems = [...values.storeKeys];
                                                                tmpItems[index] = {
                                                                    ...tmpItems[index],
                                                                    rangePrefix: e.detail.value
                                                                };
                                                                setFieldValue("storeKeys", tmpItems)
                                                            }}
                                                        />
                                                    )
                                                },
                                            ]}
                                            empty="No keys defined."
                                        />
                                    </SpaceBetween>
                                </Container>
                            </SpaceBetween>
                        </Form>
                    </form>
                );
            }}
        </CloudscapeFormik>
    );
}