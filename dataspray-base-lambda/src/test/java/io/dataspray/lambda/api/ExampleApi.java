package io.dataspray.lambda.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/")
public interface ExampleApi {
    @GET
    @Path("/ping")
    String ping();
}
