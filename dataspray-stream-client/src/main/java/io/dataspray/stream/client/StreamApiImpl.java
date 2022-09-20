package io.dataspray.stream.client;

import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.ingest.client.IngestApi;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StreamApiImpl implements StreamApi {
    @Override
    public IngestApi ingest(String apiKey) {
        io.dataspray.stream.ingest.client.ApiClient apiClient = new io.dataspray.stream.ingest.client.ApiClient();
        apiClient.setApiKey(apiKey);
        return new IngestApi(apiClient);
    }

    @Override
    public ControlApi control(String apiKey) {
        io.dataspray.stream.control.client.ApiClient apiClient = new io.dataspray.stream.control.client.ApiClient();
        apiClient.setApiKey(apiKey);
        return new ControlApi(apiClient);
    }
}
