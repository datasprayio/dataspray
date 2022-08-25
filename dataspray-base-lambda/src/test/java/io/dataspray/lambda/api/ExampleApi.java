package io.dataspray.lambda.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/")
public interface ExampleApi {
    @GET
    @Path("/ping")
    String ping();
}
