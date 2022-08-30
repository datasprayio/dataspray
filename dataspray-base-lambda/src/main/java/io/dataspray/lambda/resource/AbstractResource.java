package io.dataspray.lambda.resource;

import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

@Slf4j
@Path("/")
@Provider
public abstract class AbstractResource {
    @Context
    protected SecurityContext securityContext;
    @Context
    protected HttpHeaders headers;
    @Context
    protected UriInfo uriInfo;
}
