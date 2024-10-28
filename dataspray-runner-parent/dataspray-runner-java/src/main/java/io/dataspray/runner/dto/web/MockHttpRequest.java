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

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

@Value
@Builder(toBuilder = true)
public class MockHttpRequest implements HttpRequest, HttpRequestContext, HttpDetails {

    @Builder.Default
    String version = "2.0";

    @Builder.Default
    String rawPath = "/";

    @Builder.Default
    String rawQueryString = "";

    @Builder.Default
    List<String> cookies = List.of();

    @Builder.Default
    Map<String, String> headers = Map.of();

    @Builder.Default
    Map<String, String> queryStringParameters = Map.of();

    @Builder.Default
    String body = "";

    @Builder.Default
    boolean isBase64Encoded = false;

    @Builder.Default
    String apiId = "MockApiId";

    @Builder.Default
    String domainName = "url-id.lambda-url.us-west-2.on.aws";

    @Builder.Default
    String domainPrefix = "url-id";

    @Builder.Default
    String requestId = "MockRequestId";

    @Builder.Default
    Instant time = Instant.now();

    @Builder.Default
    String method = "GET";

    @Builder.Default
    String path = "/";

    @Builder.Default
    String protocol = "HTTP/1.1";

    @Builder.Default
    String sourceIp = "127.0.0.1";

    @Builder.Default
    String userAgent = "MockUserAgent";

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

    @Override
    public HttpRequestContext getHttpRequestContext() {
        return this;
    }

    @Override
    public HttpDetails getHttp() {
        return this;
    }

    public static class MockHttpRequestBuilder {

        /**
         * Sets the body as and sets the isBase64Encoded flag to false.
         */
        public MockHttpRequestBuilder bodyAsString(String body) {
            body$value = body;
            body$set = true;
            isBase64Encoded$value = false;
            isBase64Encoded$set = true;
            return this;
        }

        /**
         * Encodes the body as Base64 string and sets the isBase64Encoded flag.
         */
        public MockHttpRequestBuilder bodyAsBytes(byte[] body) {
            body$value = Base64.getEncoder().encodeToString(body);
            body$set = true;
            isBase64Encoded$value = true;
            isBase64Encoded$set = true;
            return this;
        }
    }
}
