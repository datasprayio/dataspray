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

import java.util.List;
import java.util.Map;

/**
 * An HTTP request event sent to your Lambda function.
 *
 * @see <a href="https://docs.aws.amazon.com/lambda/latest/dg/urls-invocation.html#urls-payloads">AWS docs</a>
 */
public interface HttpRequest {

    /**
     * The payload format version for this event. Lambda function URLs currently support payload format version 2.0.
     *
     * @see <a
     * href="https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-lambda.html#http-api-develop-integrations-lambda.proxy-format">Version
     * history</a>
     */
    String getVersion();

    /**
     * The request path. For example, if the request URL is
     * <pre>https://{url-id}.lambda-url.{region}.on.aws/example/test/demo</pre>, then the raw path value is
     * <pre>/example/test/demo</pre>.
     */
    String getRawPath();

    /**
     * The raw string containing the request's query string parameters. Supported characters include a-z, A-Z, 0-9, .,
     * _, -, %, &, =, and +.
     */
    String getRawQueryString();

    /**
     * An array containing all cookies sent as part of the request.
     */
    List<String> getCookies();

    /**
     * The list of request headers, presented as key-value pairs.
     */
    Map<String, String> getHeaders();

    /**
     * The query parameters for the request. For example, if the request URL is
     * <pre>https://{url-id}.lambda-url.{region}.on.aws/example?name=Jane</pre>, then the queryStringParameters value is
     * a JSON object with a key of name and a value of Jane.
     */
    Map<String, String> getQueryStringParameters();

    /**
     * An object that contains additional information about the request, such as the requestId, the time of the request,
     * and the identity of the caller if authorized via AWS Identity and Access Management (IAM).
     */
    HttpRequestContext getHttpRequestContext();

    /**
     * The body of the request. If the content type of the request is binary, the body is base64-encoded.
     */
    String getBody();

    /**
     * The body of the request as a String, throwing an exception if the body is binary and base64-encoded.
     *
     * @throws IllegalStateException if the body is base64-encoded
     */
    String getBodyAsString() throws IllegalStateException;

    /**
     * The body of the request as a binary array, converting from Base64 if necessary.
     */
    byte[] getBodyAsBinary();

    /**
     * TRUE if the body is a binary payload and base64-encoded. FALSE otherwise.
     */
    boolean isBase64Encoded();
}
