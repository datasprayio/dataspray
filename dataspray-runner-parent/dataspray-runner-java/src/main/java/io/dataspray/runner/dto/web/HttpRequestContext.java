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

package io.dataspray.runner.dto.web;

import java.time.Instant;

/**
 * Represents the context of an HTTP request sent to your Lambda function.
 *
 * @see <a href="https://docs.aws.amazon.com/lambda/latest/dg/urls-invocation.html#urls-payloads">AWS docs</a>
 */
public interface HttpRequestContext {

    /**
     * The ID of the function URL.
     */
    String getApiId();

    /**
     * The domain name of the function URL. Example: {@code <url-id>.lambda-url.us-west-2.on.aws}
     */
    String getDomainName();

    /**
     * The domain prefix of the function URL. Example: {@code <url-id>}
     */
    String getDomainPrefix();

    HttpDetails getHttp();

    /**
     * The ID of the invocation request. You can use this ID to trace invocation logs related to your function.
     */
    String getRequestId();

    /**
     * The timestamp of the request. Example: {@code 1631055022677}
     */
    Instant getTime();
}
