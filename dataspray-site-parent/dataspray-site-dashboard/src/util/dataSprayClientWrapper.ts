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

import {isCsr} from "./isoUtil";
import {detectEnv, Environment} from "./detectEnv";
import {BASE_PATH, DataSprayClient} from "dataspray-client";

let clientCache: DataSprayClient;

export const getClient = (): DataSprayClient => {
    if (!clientCache) {
        let basePath = BASE_PATH;
        switch (detectEnv()) {
            case Environment.STAGING:
            case Environment.LOCAL:
                basePath = BASE_PATH.replace("dataspray.io", "staging.dataspray.io");
                break;
            case Environment.SELF_HOST:
                basePath = BASE_PATH.replace("dataspray.io", window.location.host);
                break;
        }
        const fetchApi = isCsr()
                ? window.fetch.bind(window)
                : async () => {
                    throw new Error("SSR fetch is disabled");
                }
        clientCache = DataSprayClient.get({
            basePath,
            fetchApi,
        });
    }
    return clientCache;
}
