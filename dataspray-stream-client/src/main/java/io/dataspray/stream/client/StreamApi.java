package io.dataspray.stream.client;

import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.ingest.client.IngestApi;

import java.io.File;
import java.io.IOException;

public interface StreamApi {

    IngestApi ingest(String apiKey);

    ControlApi control(String apiKey);

    void uploadCode(String presignedUrl, File file) throws IOException;
}
