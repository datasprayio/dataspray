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

package io.dataspray.common.test.aws;

import io.dataspray.common.authorizer.AuthorizerConstants;
import io.dataspray.common.json.GsonUtil;
import io.quarkus.amazon.lambda.http.model.ApiGatewayAuthorizerContext;
import io.quarkus.amazon.lambda.http.model.AwsProxyRequest;
import io.quarkus.amazon.lambda.http.model.AwsProxyRequestContext;
import io.quarkus.amazon.lambda.http.model.MultiValuedTreeMap;
import io.quarkus.amazon.lambda.runtime.AmazonLambdaApi;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.ws.rs.HttpMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;

@Slf4j
public abstract class AbstractLambdaTest {

    protected AwsResponse<Void> request(Given given) {
        return request(Void.class, given);
    }

    protected <T> AwsResponse<T> request(Class<T> bodyClazz, Given given) {
        // https://quarkus.io/guides/aws-lambda-http#live-coding-and-simulating-aws-lambda-environment-locally
        AwsProxyRequest request = new AwsProxyRequest();
        request.setHttpMethod(given.getMethod());
        request.setResource(given.getPath());
        request.setPath(given.getPath());
        if (given.getQuery() != null && !given.getQuery().isEmpty()) {
            MultiValuedTreeMap<String, String> query = new MultiValuedTreeMap<>();
            query.putAll(given.getQuery());
            request.setMultiValueQueryStringParameters(query);
        }
        request.setBody(GsonUtil.get().toJson(given.getBody()));
        request.setRequestContext(new AwsProxyRequestContext());
        request.getRequestContext().setPath(given.getPath());
        request.getRequestContext().setResourcePath(given.getPath());
        request.getRequestContext().setHttpMethod(given.getMethod());
        request.getRequestContext().setAuthorizer(new ApiGatewayAuthorizerContext());
        request.getRequestContext().getAuthorizer().setPrincipalId(given.getAccountId());
        request.getRequestContext().getAuthorizer().setContextValue(AuthorizerConstants.CONTEXT_KEY_ACCOUNT_ID, given.getAccountId());
        request.getRequestContext().getAuthorizer().setContextValue(AuthorizerConstants.CONTEXT_KEY_APIKEY_VALUE, given.getApiKeyValue());
        Response response = RestAssured.given()
                .contentType("application/json")
                .accept("application/json")
                .body(request)
                .log().body()
                .when()
                .post(AmazonLambdaApi.API_BASE_PATH_TEST);
        response.then()
                .log().body()
                .assertThat()
                .statusCode(jakarta.ws.rs.core.Response.Status.OK.getStatusCode());
        T body;
        if (Void.class.equals(bodyClazz)) {
            body = null;
        } else {
            body = GsonUtil.get().fromJson((String) response.path("body"), bodyClazz);
            log.info("Received body {}", body);
        }
        return new AwsResponse<>(
                response,
                body
        );
    }

    @Value
    @Builder
    protected static class Given {
        @Builder.Default
        String method = HttpMethod.GET;
        String path;
        Map<String, String> pathParams;
        Map<String, List<String>> query;
        Object body;
        @Builder.Default
        String accountId = "123456";
        @Builder.Default
        String apiKeyValue = "B41B7CC9-BD31-46E3-8CD1-52B6A2BC203C";
    }


    @Value
    @AllArgsConstructor
    protected static class AwsResponse<T> {
        Response response;
        T body;

        public AwsResponse<T> assertStatusCode(int statusCode) {
            response.then().assertThat()
                    .body("statusCode", equalTo(statusCode));
            return this;
        }
    }
}
