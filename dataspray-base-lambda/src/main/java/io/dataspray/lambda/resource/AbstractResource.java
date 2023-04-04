package io.dataspray.lambda.resource;

import io.quarkus.amazon.lambda.http.model.AwsProxyRequest;
import io.quarkus.amazon.lambda.http.model.AwsProxyRequestContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("/")
@Provider
public abstract class AbstractResource {
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
}
