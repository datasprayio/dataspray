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
    AuthNZApi,
    AuthNZApiInterface,
    BaseAPI,
    Configuration,
    ConfigurationParameters,
    ControlApi,
    ControlApiInterface,
    HealthApi,
    HealthApiInterface,
    HTTPHeaders,
    IngestApi,
    IngestApiInterface,
    OrganizationApi,
    OrganizationApiInterface,
    QueryApi,
    QueryApiInterface
} from "./client";

export interface DataSprayClientConfig {
    apiKey?: string;
    accessToken?: string;
    basePath?: ConfigurationParameters['basePath'];
    fetchApi?: ConfigurationParameters['fetchApi'];
}

// Recommended way to create a constructor type
// https://www.typescriptlang.org/docs/handbook/2/generics.html#using-class-types-in-generics
type BaseAPIConstructor<T> = { new(conf: Configuration): T };

export class DataSprayClient {

    static get(access: DataSprayClientConfig): DataSprayClient {
        return new DataSprayClient(access);
    }

    private headers: HTTPHeaders = {};
    private config: Configuration;
    private clientCache = new Map<BaseAPIConstructor<any>, BaseAPI>();

    constructor(access: DataSprayClientConfig) {
        access.apiKey && this.setApiKey(access.apiKey);
        access.accessToken && this.setAccessToken(access.accessToken);
        this.config = new Configuration({
            basePath: access.basePath,
            // Can be cast as any: https://github.com/node-fetch/node-fetch/issues/359#issuecomment-342571083
            fetchApi: access.fetchApi,
            headers: this.headers,
        });
    }

    ingest(): IngestApiInterface {
        return this.getClient(IngestApi);
    }

    control(): ControlApiInterface {
        return this.getClient(ControlApi);
    }

    authNz(): AuthNZApiInterface {
        return this.getClient(AuthNZApi);
    }

    health(): HealthApiInterface {
        return this.getClient(HealthApi);
    }

    organization(): OrganizationApiInterface {
        return this.getClient(OrganizationApi);
    }

    query(): QueryApiInterface {
        return this.getClient(QueryApi);
    }

    async uploadCode(presignedUrl: string, file: Blob) {
        const response = await (this.config.fetchApi || fetch)(presignedUrl, {
            method: 'PUT',
            body: file,
            headers: {
                'Content-Type': 'application/zip'
            }
        });
        if (!response.ok) {
            throw new Error(`Failed with status ${response.status} to upload to S3: ${await response.text()}`);
        }
    }

    /**
     * Set the API key to be used by the client. Takes effect immediately for existing clients.
     */
    setApiKey(apiKey: string) {
        this.headers['Authorization'] = `apikey ${apiKey}`;
    }

    /**
     * Set the Cognito access token to be used by the client. Takes effect immediately for existing clients.
     */
    setAccessToken(accessToken: string) {
        this.headers['Authorization'] = `cognito ${accessToken}`;
    }

    /**
     * Unset api key/access token. Takes effect immediately for existing clients.
     */
    unsetAuth() {
        delete this.headers['Authorization'];
    }

    private getClient<T extends BaseAPI>(ctor: BaseAPIConstructor<T>): T {
        var client: T = this.clientCache.get(ctor) as T;
        if (!client) {
            client = new ctor(this.config);
            this.clientCache.set(ctor, client);
        }
        return client;
    }
}
