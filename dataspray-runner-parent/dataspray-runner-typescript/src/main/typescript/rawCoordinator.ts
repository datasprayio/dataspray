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

import {StoreType} from "./storeType";
import {DataSprayClient, IngestApiInterface} from "dataspray-client";
import {StateManager} from "./stateManager";
import {StateManagerFactoryImpl} from "./stateManagerFactory";
import {DynamoDBClient} from "@aws-sdk/client-dynamodb";

// Matches io.dataspray.store.LambdaDeployerImpl.DATASPRAY_API_KEY_ENV
const DATASPRAY_API_KEY_ENV = 'dataspray_api_key';
// Matches io.dataspray.store.LambdaDeployerImpl.DATASPRAY_ORGANIZATION_NAME_ENV
const DATASPRAY_ORGANIZATION_NAME_ENV = 'dataspray_organization_name';
// Matches io.dataspray.store.LambdaDeployerImpl.DATASPRAY_ENDPOINT_ENV
const DATASPRAY_ENDPOINT_ENV = 'dataspray_endpoint';

export interface RawCoordinator {

    send(messageKey: string, data: Blob, storeType: StoreType, storeName: string, streamName: string, messageId: string | undefined): void;

    getStateManager(key: string[], ttlInSec?: number): StateManager;

    getDynamoClient(): DynamoDBClient;
}

export class RawCoordinatorImpl implements RawCoordinator {

    private static instance: RawCoordinator | null = null;
    private ingestApi: IngestApiInterface | null = null;

    static get(): RawCoordinator {
        if (RawCoordinatorImpl.instance === null) {
            RawCoordinatorImpl.instance = new RawCoordinatorImpl();
        }
        return RawCoordinatorImpl.instance;
    }

    send(messageKey: string, data: Blob, storeType: StoreType, storeName: string, streamName: string, messageId: string | undefined = undefined): void {
        switch (storeType) {
            case StoreType.DATASPRAY:
                this.sendToDataSpray(messageKey, data, storeName, streamName, messageId);
                break;
            default:
                throw new Error(`Store type not supported: ${storeType}`);
        }
    }

    getStateManager(key: string[], ttlInSec?: number) {
        return StateManagerFactoryImpl.getOrCreate().getStateManager(key, ttlInSec);
    }

    getDynamoClient(): DynamoDBClient {
        return StateManagerFactoryImpl.getOrCreate().getDynamoClient();
    }

    private sendToDataSpray(key: string, data: Blob, organizationName: string, topicName: string, id: string | undefined = undefined): void {
        try {
            this.getIngestApi().message({
                messageKey: key,
                messageId: id,
                organizationName,
                topicName: topicName,
                body: data,
            });
        } catch (ex) {
            throw new Error(`Failed to send message to DataSpray for organization ${organizationName} topicName ${topicName}: ${ex}`);
        }
    }

    private getIngestApi(): IngestApiInterface {
        if (this.ingestApi === null) {
            // Fetch api key
            const apiKey = process.env[DATASPRAY_API_KEY_ENV];
            if (apiKey === undefined) {
                throw new Error(`DataSpray API key not found using env var ${DATASPRAY_API_KEY_ENV}`);
            }

            // Fetch organization name
            const organizationName = process.env[DATASPRAY_ORGANIZATION_NAME_ENV];
            if (organizationName === undefined) {
                throw new Error(`DataSpray organization name not found using env var ${DATASPRAY_ORGANIZATION_NAME_ENV}`);
            }

            // Fetch endpoint
            const basePath = process.env[DATASPRAY_ENDPOINT_ENV] || undefined;

            this.ingestApi = DataSprayClient.get({apiKey, basePath}).ingest();
        }
        return this.ingestApi;
    }
}
