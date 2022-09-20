package io.dataspray.stream.client;

import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.ingest.client.IngestApi;

public interface StreamApi {

    IngestApi ingest(String apiKey);

    ControlApi control(String apiKey);
}
