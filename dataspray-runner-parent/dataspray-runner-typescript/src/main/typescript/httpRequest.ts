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

import {LambdaFunctionURLEvent} from "aws-lambda";

export interface HttpRequest extends LambdaFunctionURLEvent {

    /**
     * The body of the request as a String, throwing an exception if the body is binary and base64-encoded.
     *
     * @throws Error if the body is base64-encoded
     */
    bodyAsString: () => string;

    /**
     * The body of the request as a binary array, converting from Base64 if necessary.
     */
    bodyAsBinary: () => Buffer;

    /**
     * The cookies of the request as a Map wiht lower-case keys.
     */
    cookiesMap: Map<string, string>;

    /**
     * The headers of the request as a Map with lower-case keys.
     */
    headersMap: Map<string, string>;
}

export const toHttpRequest = (event: LambdaFunctionURLEvent): HttpRequest => ({
    ...event,
    bodyAsBinary: () => {
        if (event.isBase64Encoded) {
            return Buffer.from(event.body!, 'base64');
        } else {
            return Buffer.from(event.body || '');
        }
    },
    bodyAsString: () => {
        if (event.isBase64Encoded) {
            throw new Error('Body is base64-encoded');
        } else {
            return event.body || '';
        }
    },
    cookiesMap: new Map((event.cookies || []).map(cookie => {
        const index = cookie.indexOf('=');
        return index === -1
                ? [cookie.toLowerCase(), '']
                : [cookie.substring(0, index).toLowerCase(), cookie.substring(index + 1)];
    })),
    headersMap: new Map(Object.entries(event.headers || {})
            .filter(([key, value]) => value !== undefined)
            .map(([key, value]) => [key.toLowerCase(), value as string])),
});
