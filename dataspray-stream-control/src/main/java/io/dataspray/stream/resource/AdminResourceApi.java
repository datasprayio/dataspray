package io.dataspray.stream.resource;

import com.google.inject.Singleton;
import io.dataspray.stream.server.PingApi;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AdminResourceApi extends AbstractResource implements PingApi {

    @Override
    public void ping() {
        log.trace("Received ping");
    }
}
