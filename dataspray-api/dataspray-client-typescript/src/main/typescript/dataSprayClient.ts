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

import {Configuration, ControlApi, ControlApiInterface, IngestApi, IngestApiInterface} from "./client";
import fetch from 'node-fetch';

export interface Access {
    apiKey: string;
    endpoint?: string;
}

export class DataSprayClient {

    static get(access: Access): DataSprayClient {
        return new DataSprayClient(access);
    }

    private config: Configuration;

    constructor(access: Access) {
        this.config = new Configuration({
            apiKey: access.apiKey,
            basePath: access.endpoint,
            fetchApi: fetch as any, // Can be cast as any: https://github.com/node-fetch/node-fetch/issues/359#issuecomment-342571083
        });
    }

    ingest(): IngestApiInterface {
        return new IngestApi(this.config);
    }

    control(): ControlApiInterface {
        return new ControlApi(this.config);
    }

    async uploadCode(presignedUrl: string, file: Blob) {
        const response = await fetch(presignedUrl, {
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
}
