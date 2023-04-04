package io.dataspray.stream.control;

import io.dataspray.lambda.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class PingResource extends AbstractResource implements PingApi {

    @Override
    public void ping() {
        log.debug("Received ping");
    }
}
