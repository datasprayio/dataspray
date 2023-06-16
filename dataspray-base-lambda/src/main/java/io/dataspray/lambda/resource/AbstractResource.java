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

package io.dataspray.lambda.resource;

import com.google.common.base.Strings;
import io.quarkus.amazon.lambda.http.model.AwsProxyRequest;
import io.quarkus.amazon.lambda.http.model.AwsProxyRequestContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@Path("/")
@Provider
public abstract class AbstractResource {

    public static final String API_GATEWAY_API_KEY_ID_HEADER_NAME = "";

    /** Can be supplied via header, query param */
    public static final String API_TOKEN_HEADER_NAME = "x-api-key";
    public static final String API_TOKEN_QUERY_NAME = "api_key";
    public static final String API_TOKEN_COOKIE_NAME = "x-api-key";
    public static final String API_TOKEN_AUTHORIZATION_TYPE = "bearer";

    @Context
    protected com.amazonaws.services.lambda.runtime.Context lambdaContext;
    @Context
    protected AwsProxyRequestContext proxyRequestContext;
    @Context
    protected AwsProxyRequest proxyRequest;
    @Context
    protected SecurityContext securityContext;
    @Context
    protected HttpHeaders headers;
    @Context
    protected UriInfo uriInfo;

    protected Optional<String> getCustomerId() {
//        proxyRequestContext.getIdentity().getApiKey()CognitoIdentityId()
//        return Optional.ofNullable(lambdaContext.getIdentity()).getIdentityId()
        throw new ServerErrorException(Response.Status.NOT_IMPLEMENTED);
    }

    protected Optional<String> getAuthKey() {
        // First check api header
        return headers.getRequestHeader(API_TOKEN_HEADER_NAME).stream().findFirst()
                .filter(Predicate.not(Strings::isNullOrEmpty))
                // Then check authorization header
                .or(() -> {
                    List<String> authorizationHeaderValues = headers.getRequestHeader(HttpHeaders.AUTHORIZATION);
                    if (authorizationHeaderValues.size() != 2
                        || !API_TOKEN_AUTHORIZATION_TYPE.equalsIgnoreCase(authorizationHeaderValues.get(0))
                        || Strings.isNullOrEmpty(authorizationHeaderValues.get(1))) {
                        return Optional.empty();
                    }
                    return Optional.of(authorizationHeaderValues.get(1));
                })
                .or(() -> Optional.ofNullable(headers.getCookies().get(API_TOKEN_COOKIE_NAME))
                        .map(Cookie::getValue))
                // Then check query param
                .or(() -> Optional.ofNullable(uriInfo.getQueryParameters().get(API_TOKEN_QUERY_NAME))
                        .flatMap(values -> values.stream().findFirst()));
    }
}
