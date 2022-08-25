package io.dataspray.stream.ingest;

import io.dataspray.lambda.resource.AbstractResource;
import io.dataspray.stream.server.PingApi;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;

@Slf4j
@ApplicationScoped
public class PingResource extends AbstractResource implements PingApi {

    @Override
    public void ping() {
        log.trace("Received ping");
    }
}
