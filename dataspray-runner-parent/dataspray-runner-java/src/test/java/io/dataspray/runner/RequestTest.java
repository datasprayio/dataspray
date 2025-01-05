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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dataspray.runner.dto.Request;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class RequestTest {

    @Test
    public void testDeserialization() throws Exception {
        String json = """
                {
                  "records": [
                    {
                      "messageId": "12345",
                      "body": "test message"
                    }
                  ],
                  "version": "1.0",
                  "rawPath": "/example/path",
                  "rawQueryString": "param1=value1&param2=value2",
                  "cookies": ["sessionId=abc123", "user=JohnDoe"],
                  "headers": {
                    "Content-Type": "application/json",
                    "Authorization": "Bearer token"
                  },
                  "queryStringParameters": {
                    "param1": "value1",
                    "param2": "value2"
                  },
                  "httpRequestContext": null,
                  "body": "{\\"key\\":\\"value\\"}",
                  "isBase64Encoded": false
                }
                """;

        // Configure ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true);

        // Deserialize JSON into Request object
        Request request = objectMapper.readValue(json, Request.class);

        // Assertions to verify deserialization
        assertNotNull(request);
        assertEquals("1.0", request.getVersion());
        assertEquals("/example/path", request.getRawPath());
        assertEquals("param1=value1&param2=value2", request.getRawQueryString());

        // Verify cookies
        assertNotNull(request.getCookies());
        assertEquals(2, request.getCookies().size());
        assertEquals("sessionId=abc123", request.getCookies().get(0));

        // Verify headers
        Map<String, String> headers = request.getHeadersCaseInsensitive();
        assertNotNull(headers);
        assertEquals("application/json", headers.get("Content-Type"));

        // Verify query string parameters
        Map<String, String> queryParams = request.getQueryStringParameters();
        assertNotNull(queryParams);
        assertEquals("value1", queryParams.get("param1"));

        // Verify body and other fields
        assertEquals("{\"key\":\"value\"}", request.getBody());
        assertFalse(request.isBase64Encoded());
    }
}
