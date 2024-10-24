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

import {
    APIGatewayProxyStructuredResultV2,
    Handler,
    LambdaFunctionURLEvent,
    SQSBatchResponse,
    SQSEvent
} from 'aws-lambda';
import {StoreType} from './storeType';
import {RawCoordinator} from './rawCoordinator';
import {MessageMetadata} from "./message";

export abstract class Entrypoint {

    readonly sqsArnPattern = /customer-(?<customer>[^-]+)-(?<queue>.+)/;

    handleRequest: Handler = async (event, context) => {
        if (!!event['Records']) {
            return this.handleSqsEvent(event as SQSEvent);
        } else if (!!event['rawPath']) {
            return this.handleHttpRequest(event as LambdaFunctionURLEvent);
        } else {
            throw new Error(`Unsupported event: ${event}`);
        }
    }

    handleSqsEvent = async (event: SQSEvent): Promise<SQSBatchResponse | void> => {
        const sqsBatchResponse: SQSBatchResponse = {
            batchItemFailures: [],
        };

        for (const msg of event.Records) {
            try {
                const {customer, queue} = this.sqsArnPattern.exec(msg.eventSourceARN)?.groups || {};
                if (!customer || !queue) {
                    throw new Error(`Failed to determine source queue from ARN ${msg.eventSourceARN}`)
                }
                await this.processSqsEvent(
                        {
                            storeType: StoreType.DATASPRAY,
                            storeName: customer,
                            streamName: queue,
                        },
                        msg.body,
                        RawCoordinator.get());
            } catch (error) {
                sqsBatchResponse.batchItemFailures
                        .push({itemIdentifier: msg.messageId});
            }
        }

        return sqsBatchResponse;
    }

    handleHttpRequest = async (request: LambdaFunctionURLEvent): Promise<APIGatewayProxyStructuredResultV2> => {
        return this.processFunctionUrl(request);
    }

    abstract processSqsEvent(
            metadata: MessageMetadata,
            data: string,
            rawCoordinator: RawCoordinator,
    ): Promise<void> | void;

    abstract processFunctionUrl(
            request: LambdaFunctionURLEvent,
    ): Promise<APIGatewayProxyStructuredResultV2>;
}
