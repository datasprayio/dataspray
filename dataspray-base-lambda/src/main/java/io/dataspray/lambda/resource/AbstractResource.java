package io.dataspray.lambda.resource;

import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

@Slf4j
@Path("/")
public abstract class AbstractResource {
    @Context
    protected SecurityContext securityContext;
}
