/*
 * Copyright 2023 Matus Faro
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

import * as Api from "../../client";

// Recommended way to create a constructor type
// https://www.typescriptlang.org/docs/handbook/2/generics.html#using-class-types-in-generics
type BaseAPIConstructor<T> = { new(conf: Api.Configuration): T };

const clientCache = new Map<BaseAPIConstructor<any>, Api.BaseAPI>();
var confCache: Api.Configuration;

export const getClientControl = (): Api.ControlApiInterface => getClient(Api.ControlApi);
export const getClientIngest = (): Api.IngestApiInterface => getClient(Api.IngestApi);
export const getClientAuth = (): Api.AuthNZApiInterface => getClient(Api.AuthNZApi);
export const getClientHealth = (): Api.HealthApiInterface => getClient(Api.HealthApi);

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
        const apiConf: Api.ConfigurationParameters = {
            fetchApi: window.fetch.bind(window),
            basePath: Api.BASE_PATH,
        };
        confCache = new Api.Configuration(apiConf)
    }
    return confCache;
}
