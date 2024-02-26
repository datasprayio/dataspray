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

import {Context, SQSBatchResponse, SQSEvent, SQSHandler} from 'aws-lambda';
import {StoreType} from './storeType';
import {RawCoordinator} from './rawCoordinator';
import {MessageMetadata} from "./message";

export abstract class Entrypoint {

    readonly sqsArnPattern = /customer-(?<customer>[^-]+)-(?<queue>.+)/;

    handle: SQSHandler = async (event: SQSEvent, context: Context): Promise<SQSBatchResponse | void> => {
        const sqsBatchResponse: SQSBatchResponse = {
            batchItemFailures: [],
        };

        for (const msg of event.Records) {
            try {
                const {customer, queue} = this.sqsArnPattern.exec(msg.eventSourceARN)?.groups || {};
                if (!customer || !queue) {
                    throw new Error(`Failed to determine source queue from ARN ${msg.eventSourceARN}`)
                }
                await this.process(
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

    abstract process(
            metadata: MessageMetadata,
            data: string,
            rawCoordinator: RawCoordinator,
    ): Promise<void> | void;
}
