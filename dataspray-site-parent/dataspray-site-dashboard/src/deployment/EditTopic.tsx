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

export function EditTopic(props: {
    topicName?: string; // name to edit or create new on empty
    topic?: Topic; // name to edit or create new on empty
    onUpdated?: (topics: Topics) => void;
}) {
    return (
        <>
            <CloudscapeFormik<{
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
                onSubmit={(values) => {
                    // TODO
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
                }) => (

                    <form onSubmit={handleSubmit}>
                        <Form
                            header={<Header variant="h1">{!!props.topicName ? 'Edit' : 'Create'} topic</Header>}
                        >
                            <SpaceBetween direction="vertical" size="l">
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
                                <Container header={<Header variant="h2">Send to Data Lake</Header>}>
                                    <SpaceBetween size="m">
                                        <FormField
                                            errorText={errors?.batchEnabled}
                                        >
                                            <Checkbox
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
                                                disabled={!values.batchEnabled}
                                                selectedOption={BATCH_RETENTION.get(values.batchRetention)!}
                                                onChange={e => setFieldValue("batchRetention", e.detail.selectedOption?.value)}
                                                options={BATCH_RETENTION_VALUES}
                                            />
                                        </FormField>
                                    </SpaceBetween>
                                </Container>
                                <Container header={<Header variant="h2">Send to Stream processing</Header>}>
                                    <SpaceBetween size="m">
                                    </SpaceBetween>
                                </Container>
                                <Container header={<Header variant="h2">Store as state</Header>}>
                                    <SpaceBetween size="m">
                                        <FormField
                                            errorText={errors?.storeEnabled}
                                        >
                                            <Checkbox
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
                                                    disabled={!values.storeEnabled}
                                                    onChangeNative={handleChange}
                                                    onBlurNative={handleBlur}
                                                    value={values.storeTtlInSec}
                                                />
                                            </FormField>
                                            <FormField
                                                label="Event key whitelist"
                                                errorText={errors?.storeWhitelist}
                                            >
                                                <Input
                                                    type="number"
                                                    placeholder='key1,key2,...'
                                                    disabled={!values.storeEnabled}
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
                                                    disabled={!values.storeEnabled}
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
                                            disableAddButton={!values.storeEnabled}
                                            isItemRemovable={() => values.storeEnabled}
                                            definition={[
                                                {
                                                    label: "Type",
                                                    control: (item, index) => (
                                                        <Select
                                                            disabled={!values.storeEnabled}
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
                                                            disabled={!values.storeEnabled}
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
                                                            disabled={!values.storeEnabled}
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
                                                            disabled={!values.storeEnabled}
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
                                                            disabled={!values.storeEnabled}
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
                )}
            </CloudscapeFormik>
        </>
    );
}