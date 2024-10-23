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

package io.dataspray.runner;

import lombok.Data;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

@Data
public class Request implements SqsRequest, HttpRequest {

    // Sqs request fields

    List<SqsMessage> records;

    // Http request fields

    String version;

    String rawPath;

    String rawQueryString;

    List<String> cookies;

    Map<String, String> headers;

    Map<String, String> queryStringParameters;

    HttpRequestContextImpl httpRequestContext;

    String body;

    boolean isBase64Encoded;

    @Override
    public String getBodyAsString() throws IllegalStateException {
        checkState(!isBase64Encoded, "Body is binary, call getBodyAsBinary() instead");
        return body;
    }

    @Override
    public byte[] getBodyAsBinary() {
        if (isBase64Encoded) {
            return Base64.getDecoder().decode(body);
        } else {
            return body.getBytes();
        }
    }

    public boolean isSqsRequest() {
        return records != null;
    }

    public boolean isHttpRequest() {
        return httpRequestContext != null;
    }
}
