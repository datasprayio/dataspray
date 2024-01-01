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

package io.dataspray.web.resource;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import io.dataspray.common.authorizer.AuthorizerConstants;
import io.quarkus.amazon.lambda.http.model.AwsProxyRequest;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@Path("/")
@Provider
public abstract class AbstractResource {

    @Context
    protected com.amazonaws.services.lambda.runtime.Context lambdaContext;
    @Context
    protected AwsProxyRequest proxyRequest;
    @Context
    protected SecurityContext securityContext;
    @Context
    protected HttpHeaders headers;
    @Context
    protected UriInfo uriInfo;

    /**
     * Retrieve user email as passed in via context from Cognito Authorizer function.
     * <p>
     * May be empty if authorizer did not process this request, most likely as this API endpoint is open to public.
     */
    protected Optional<String> getUserEmail() {
        return Optional.ofNullable(Strings.emptyToNull(proxyRequest
                .getRequestContext()
                .getAuthorizer()
                .getContextValue(AuthorizerConstants.CONTEXT_KEY_USER_EMAIL)));
    }

    /**
     * Retrieve user organization names as passed in via context from Cognito Authorizer function.
     * <p>
     * May be empty if authorizer did not process this request, most likely as this API endpoint is open to public.
     */
    protected ImmutableSet<String> getOrganizationNames() {
        return Optional.ofNullable(Strings.emptyToNull(proxyRequest
                        .getRequestContext()
                        .getAuthorizer()
                        .getContextValue(AuthorizerConstants.CONTEXT_KEY_ORGANIZATION_NAMES)))
                .stream()
                .flatMap(names -> ImmutableSet.copyOf(names.split(",")).stream())
                .filter(Predicate.not(Strings::isNullOrEmpty))
                .collect(ImmutableSet.toImmutableSet());
    }
}
