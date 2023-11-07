/*
 * Copyright 2023 Matus Faro
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

package io.dataspray.authorizer;


import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;

/**
 * Signal to API Gateway to return client a 401.
 * <br />
 * If the client token is not recognized or invalid, API Gateway
 * accepts a 401 Unauthorized response to the client by failing like so.
 * <pre>throw new RuntimeException("Unauthorized");</pre>
 * Although return a String "Unauthorized" should also suffice
 *
 * @see <a
 * href="https://github.com/awslabs/aws-apigateway-lambda-authorizer-blueprints/blob/master/blueprints/java/src/example/APIGatewayAuthorizerHandler.java#L42">Example
 * throwing exception</a>
 * @see <a
 * href="https://stackoverflow.com/questions/64909861/how-to-return-401-from-aws-lambda-authorizer-without-raising-an-exception#comment132887021_66788544">Example
 * returning string</a>
 */
@RegisterForReflection
public class ApiGatewayUnauthorized extends RuntimeException {

    private static final String API_GATEWAY_UNAUTHORIZED_RETURN_VALUE = "Unauthorized";

    @Getter
    private final String reason;

    /**
     * This ensures Lambda will return a 401 to API Gateway if this exception is thrown
     */
    public ApiGatewayUnauthorized(String reason) {
        super(API_GATEWAY_UNAUTHORIZED_RETURN_VALUE);
        this.reason = reason;
    }

    /**
     * This serializes this object to the literal string if Jackson is used
     */
    @JsonValue
    @JsonRawValue
    public String getApiGatewayUnauthorizedReturnValue() {
        return API_GATEWAY_UNAUTHORIZED_RETURN_VALUE;
    }
}
