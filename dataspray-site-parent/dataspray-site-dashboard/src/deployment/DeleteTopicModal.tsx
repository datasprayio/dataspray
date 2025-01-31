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

import {Box, ColumnLayout, Modal} from "@cloudscape-design/components";
import React from "react";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";
import FormField from "@cloudscape-design/components/form-field";
import Input from "@cloudscape-design/components/input";

export const DeleteTopicModal = (props: {
    topicName?: string;
    show: boolean;
    onHide: () => void;
    onDelete: () => void;
}) => {
    const [deleteInputText, setDeleteInputText] = React.useState('');
    const inputMatchesConsentText = props.topicName?.toLowerCase() === deleteInputText.toLowerCase();

    return (
            <Modal
                    visible={props.show}
                    onDismiss={props.onHide}
                    header='Delete Topic'
                    closeAriaLabel='Close dialog'
                    footer={
                        <Box float="right">
                            <SpaceBetween direction="horizontal" size="xs">
                                <Button onClick={props.onHide}>
                                    Cancel
                                </Button>
                                <Button variant="primary" onClick={props.onDelete} disabled={!inputMatchesConsentText}>
                                    Delete
                                </Button>
                            </SpaceBetween>
                        </Box>
                    }
            >
                <SpaceBetween size="m">
                    <Box variant="span">
                        Permanently delete Topic{' '}
                        <Box variant="span" fontWeight="bold">
                            {props.topicName}
                        </Box>
                        {' '}?
                        Any data sent to this topic will be bound by the default topic.
                    </Box>

                    <Box>To avoid accidental deletions, we ask you to provide additional written consent.</Box>

                    <form onSubmit={props.onDelete}>
                        <FormField label={`To confirm this deletion, type "${props.topicName}".`}>
                            <ColumnLayout columns={2}>
                                <Input
                                        placeholder={props.topicName}
                                        onChange={event => setDeleteInputText(event.detail.value)}
                                        value={deleteInputText}
                                        ariaRequired={true}
                                />
                            </ColumnLayout>
                        </FormField>
                    </form>
                </SpaceBetween>
            </Modal>
    );
};

