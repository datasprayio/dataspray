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
import {ColumnLayout, Container, Header, SpaceBetween} from '@cloudscape-design/components';
import {TaskStatus} from "dataspray-client";
import Link from "@cloudscape-design/components/link";

const TopicLink = (props: {
    topicName: string;
}) => {
    return (
        <Link href={`/deployment/topic/${props.topicName}`}>{props.topicName}</Link>
    );
};

export const ViewTask = (props: {
    task: TaskStatus;
}) => {
    return (
        <SpaceBetween direction="vertical" size="l">
            <Container header={<Header variant="h2">Info</Header>}>
                <ColumnLayout columns={2}>
                    <p>Task Name</p>
                    <h3>{props.task.taskId}</h3>
                    <p>Deploy version</p>
                    <h3>{props.task.version}</h3>
                </ColumnLayout>
            </Container>
            <Container header={<Header variant="h2">Streaming</Header>}>
                <ColumnLayout columns={2}>
                    <p>Input Topics</p>
                    <h3>
                        {props.task.inputQueueNames?.map(queueName => (
                            <TopicLink key={queueName} topicName={queueName}/>
                        )) || 'None'}
                    </h3>
                    <p>Output Topics</p>
                    <h3>
                        {props.task.outputQueueNames?.map(queueName => (
                            <TopicLink key={queueName} topicName={queueName}/>
                        )) || 'None'}
                    </h3>
                </ColumnLayout>
            </Container>
            <Container header={<Header variant="h2">Web</Header>}>
                <ColumnLayout columns={2}>
                    <p>Endpoint URL</p>
                    <h3>
                        {!!props.task.endpointUrl ? (
                            <Link external
                                  href={props.task.endpointUrl}>{props.task.endpointUrl}</Link>
                        ) : 'None'}
                    </h3>
                </ColumnLayout>
            </Container>
        </SpaceBetween>
    );
}