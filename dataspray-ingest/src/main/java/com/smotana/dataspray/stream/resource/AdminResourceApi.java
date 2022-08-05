package com.smotana.dataspray.stream.resource;

import com.google.inject.Singleton;
import io.dataspray.stream.server.AdminApi;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AdminResourceApi extends AbstractResource implements AdminApi {
    @Override
    public void ping() {
        log.trace("Received ping");
    }
}
