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

import {Url} from "next/dist/shared/lib/router/router";
import {parse} from "querystring";
import type {UrlObject} from "url";

/**
 * Workaround For NextJS Router, hides the given params from the url. Effectivelly the same as
 * React Router's state, but for NextJS.
 *
 * <pre>
 *  await router.push(...urlWithHiddenParams({
 *          pathname: '/auth/confirm',
 *          query: { email, password },
 *      }, 'email', 'password'))
 * </pre>
 *
 * @see https://github.com/vercel/next.js/issues/771
 * @param url
 * @param hiddenQueryParams
 */
export const urlWithHiddenParams = (url: UrlObject, ...hiddenQueryParams: string[]): [Url, Url] => {
    if (!url.query) {
        return [url, url];
    }
    const querySanitized = typeof url.query === 'string'
        ? parse(url.query)
        : url.query;
    hiddenQueryParams.forEach(key => {
        delete querySanitized[key];
    })
    return [
        url,
        {
            ...url,
            query: querySanitized,
        }
    ]
}