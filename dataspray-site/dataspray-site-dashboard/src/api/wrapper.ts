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

import * as Api from "./client";
import {HTTPHeaders} from "./client";
import {isCsr} from "../util/isoUtil";
import {detectEnv, Environment} from "../util/detectEnv";

// Recommended way to create a constructor type
// https://www.typescriptlang.org/docs/handbook/2/generics.html#using-class-types-in-generics
type BaseAPIConstructor<T> = { new(conf: Api.Configuration): T };

const clientCache = new Map<BaseAPIConstructor<any>, Api.BaseAPI>();
var confCache: Api.Configuration;
var headers: HTTPHeaders = {};

const getClient = <T extends Api.BaseAPI>(ctor: BaseAPIConstructor<T>): T => {
    var client: T = clientCache.get(ctor) as T;
    if (!client) {
        client = new ctor(getClientConfiguration());
        clientCache.set(ctor, client);
    }
    return client;
}

const getClientConfiguration = (): Api.Configuration => {
    if (!confCache) {
        const fetchApi = isCsr()
            ? window.fetch.bind(window)
            : async () => {
                throw new Error("SSR fetch is disabled");
            }
        let basePath = Api.BASE_PATH;
        switch (detectEnv()) {
            case Environment.STAGING:
            case Environment.LOCAL:
                basePath = Api.BASE_PATH.replace("dataspray.io", "staging.dataspray.io");
                break;
            case Environment.SELF_HOST:
                basePath = Api.BASE_PATH.replace("dataspray.io", window.location.host);
                break;
        }
        confCache = new Api.Configuration({
            fetchApi,
            basePath,
            headers,
        })
    }
    return confCache;
}

/**
 * Fetch the client instance for the given API type
 */
export const getClientControl = (): Api.ControlApiInterface => getClient(Api.ControlApi);
export const getClientIngest = (): Api.IngestApiInterface => getClient(Api.IngestApi);
export const getClientAuth = (): Api.AuthNZApiInterface => getClient(Api.AuthNZApi);
export const getClientHealth = (): Api.HealthApiInterface => getClient(Api.HealthApi);
/**
 * Set the API key to be used by the client. Takes effect immediately for existing clients.
 */
export const setApiKey = (apiKey: string) => headers['Authorization'] = `apikey ${apiKey}`;
/**
 * Set the Cognito access token to be used by the client. Takes effect immediately for existing clients.
 */
export const setAccessToken = (accessToken: string) => headers['Authorization'] = `cognito ${accessToken}`;
