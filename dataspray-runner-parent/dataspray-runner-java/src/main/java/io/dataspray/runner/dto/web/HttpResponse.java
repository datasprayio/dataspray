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

import io.dataspray.runner.util.GsonUtil;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * @see <a href="https://docs.aws.amazon.com/lambda/latest/dg/urls-invocation.html#urls-payloads">AWS docs</a>
 */
@Value
@Builder(toBuilder = true)
public class HttpResponse {

    @Builder.Default
    int statusCode = 204;

    @Singular
    Map<String, String> headers;

    @Singular
    List<String> cookies;

    @Builder.Default
    String body = "";

    @Builder.Default
    boolean isBase64Encoded = false;

    public static class HttpResponseBuilder<T> {

        /**
         * The server successfully processed the request, and is not returning any content.
         */
        public HttpResponseBuilder<T> ok() {
            return statusCode(204);
        }

        /**
         * The server successfully processed the request, and is returning content.
         */
        public HttpResponseBuilder<T> ok(String bodyStr) {
            return statusCode(200)
                    .body(bodyStr);
        }

        /**
         * The server successfully processed the request, and is returning content.
         */
        public HttpResponseBuilder<T> ok(byte[] bodyBytes) {
            return statusCode(200)
                    .body(bodyBytes);
        }

        /**
         * The requested resource could not be found but may be available in the future.
         */
        public HttpResponseBuilder<T> notFound() {
            return statusCode(404);
        }

        /**
         * The request contained valid data and was understood by the server, but the server is refusing action.
         */
        public HttpResponseBuilder<T> forbidden() {
            return statusCode(403);
        }

        /**
         * Similar to 403 Forbidden, but specifically for use when authentication is required and has failed or has not
         * yet been provided.
         */
        public HttpResponseBuilder<T> unauthorized() {
            return statusCode(401);
        }

        /**
         * A generic server error has occurred.
         */
        public HttpResponseBuilder<T> internalServerError() {
            return statusCode(500);
        }

        /**
         * Tea pot requesting to brew coffee.
         */
        public HttpResponseBuilder<T> teaPot() {
            return statusCode(418);
        }

        /**
         * String body without base64 encoding.
         */
        public HttpResponseBuilder<T> body(String bodyStr) {
            this.isBase64Encoded(false);
            this.body$value = bodyStr;
            this.body$set = true;
            if (this.statusCode$value == 204) {
                this.statusCode(200);
            }
            return this;
        }

        /**
         * Binary body with base64 encoding.
         */
        public HttpResponseBuilder<T> body(byte[] bodyBytes) {
            this.isBase64Encoded(true);
            this.body$value = Base64.getEncoder().encodeToString(bodyBytes);
            this.body$set = true;
            if (this.statusCode$value == 204) {
                this.statusCode(200);
            }
            return this;
        }

        /**
         * Custom type as body.
         */
        public HttpResponseBuilder<T> body(T body) {
            body(GsonUtil.get().toJson(body));
            return this;
        }
    }
}
