package io.dataspray.stream.ingest;

import io.dataspray.lambda.resource.AbstractResource;
import io.dataspray.stream.server.IngestApi;
import io.dataspray.stream.server.model.Error;

public class IngestResource extends AbstractResource implements IngestApi {
    @Override
    public void message(String accountId, String targetId) {
        // TODO
    }
}
