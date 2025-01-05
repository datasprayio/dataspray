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

import io.dataspray.runner.dto.Request;
import io.dataspray.runner.util.GsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class RequestTest {

    @Test
    public void testDeserialization() throws Exception {
        // From https://docs.aws.amazon.com/lambda/latest/dg/urls-invocation.html#urls-payloads
        String json = """
                {
                  "version": "2.0",
                  "routeKey": "$default",
                  "rawPath": "/my/path",
                  "rawQueryString": "parameter1=value1&parameter1=value2&parameter2=value",
                  "cookies": [
                    "cookie1",
                    "cookie2"
                  ],
                  "headers": {
                    "header1": "value1",
                    "header2": "value1,value2"
                  },
                  "queryStringParameters": {
                    "parameter1": "value1,value2",
                    "parameter2": "value"
                  },
                  "requestContext": {
                    "accountId": "123456789012",
                    "apiId": "<urlid>",
                    "authentication": null,
                
                    "authorizer": {
                        "iam": {
                                "accessKey": "AKIA...",
                                "accountId": "111122223333",
                                "callerId": "AIDA...",
                                "cognitoIdentity": null,
                                "principalOrgId": null,
                                "userArn": "arn:aws:iam::111122223333:user/example-user",
                                "userId": "AIDA..."
                        }
                    },
                    "domainName": "<url-id>.lambda-url.us-west-2.on.aws",
                    "domainPrefix": "<url-id>",
                    "http": {
                      "method": "POST",
                      "path": "/my/path",
                      "protocol": "HTTP/1.1",
                      "sourceIp": "123.123.123.123",
                      "userAgent": "agent"
                    },
                    "requestId": "id",
                    "routeKey": "$default",
                    "stage": "$default",
                    "time": "12/Mar/2020:19:03:58 +0000",
                    "timeEpoch": 1583348638390
                  },
                  "body": "Hello from client!",
                  "pathParameters": null,
                  "isBase64Encoded": false,
                  "stageVariables": null
                }
                """;

        // Deserialize JSON into Request object
        Request request = GsonUtil.get().fromJson(json, Request.class);

        // Assertions to verify deserialization
        assertNotNull(request);
        assertEquals("2.0", request.getVersion());
        assertEquals("/my/path", request.getRawPath());
        assertEquals("parameter1=value1&parameter1=value2&parameter2=value", request.getRawQueryString());

        // Verify cookies
        assertNotNull(request.getCookies());
        assertEquals(2, request.getCookies().size());
        assertEquals("cookie1", request.getCookies().get(0));

        // Verify headers
        Map<String, String> headers = request.getHeadersCaseInsensitive();
        assertNotNull(headers);
        assertEquals("value1", headers.get("HEADER1"));

        // Verify query string parameters
        Map<String, String> queryParams = request.getQueryStringParameters();
        assertNotNull(queryParams);
        assertEquals("value1,value2", queryParams.get("parameter1"));

        // Verify request context
        assertNotNull(request.getRequestContext());
        assertEquals("id", request.getRequestContext().getRequestId());
        assertNotNull(request.getRequestContext().getHttp());
        assertEquals("POST", request.getRequestContext().getHttp().getMethod());
        assertEquals("/my/path", request.getRequestContext().getHttp().getPath());

        // Verify body and other fields
        assertEquals("Hello from client!", request.getBody());
        assertFalse(request.isBase64Encoded());
    }
}
