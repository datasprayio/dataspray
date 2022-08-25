package io.dataspray.lambda.resource;


import io.dataspray.lambda.api.ExampleApi;

public class ExampleResource extends AbstractResource implements ExampleApi {
    @Override
    public String ping() {
        return "OK";
    }
}
