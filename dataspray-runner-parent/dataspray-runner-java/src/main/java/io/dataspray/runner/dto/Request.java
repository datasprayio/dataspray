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

package io.dataspray.runner.dto;

import com.google.common.collect.Maps;
import io.dataspray.runner.dto.sqs.SqsMessage;
import io.dataspray.runner.dto.sqs.SqsRequest;
import io.dataspray.runner.dto.web.HttpRequest;
import io.dataspray.runner.dto.web.HttpRequestContextImpl;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Value
public class Request implements SqsRequest, HttpRequest {

    // Sqs request fields

    List<SqsMessage> records;

    // Http request fields

    String version;

    String rawPath;

    String rawQueryString;

    List<String> cookies;

    @NonFinal
    transient Map<String, String> cookieMap;

    @Override
    public Map<String, String> getCookiesCaseInsensitive() {
        if (this.cookieMap == null) {
            this.cookieMap = getCookies().stream()
                    .map(cookie -> cookie.split("=", 2))
                    .collect(Collectors.toMap(
                            cookie -> cookie[0],
                            cookie -> cookie.length > 1 ? cookie[1] : "",
                            (existing, replacement) -> existing + "; " + replacement,
                            () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
        }
        return this.cookieMap;
    }

    Map<String, String> headers;

    @NonFinal
    transient Map<String, String> headersCaseInsensitive;

    public Map<String, String> getHeadersCaseInsensitive() {
        if (this.headersCaseInsensitive == null) {
            TreeMap<String, String> headersCaseInsensitive = Maps.newTreeMap(String.CASE_INSENSITIVE_ORDER);
            headersCaseInsensitive.putAll(this.headers);
            this.headersCaseInsensitive = headersCaseInsensitive;
        }
        return this.headersCaseInsensitive;
    }

    Map<String, String> queryStringParameters;

    HttpRequestContextImpl httpRequestContext;

    String body;

    boolean isBase64Encoded;

    public boolean isSqsRequest() {
        return records != null;
    }

    public boolean isHttpRequest() {
        return rawPath != null;
    }
}
