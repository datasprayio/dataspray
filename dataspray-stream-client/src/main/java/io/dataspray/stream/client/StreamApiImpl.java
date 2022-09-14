package io.dataspray.stream.client;

import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.ingest.client.IngestApi;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StreamApiImpl implements StreamApi {
    private volatile IngestApi ingestApi;
    private volatile ControlApi controlApi;

    @Override
    public IngestApi ingest() {
        if (ingestApi == null) {
            synchronized (StreamApiImpl.class) {
                if (ingestApi == null) {
                    ingestApi = new IngestApi();
                }
            }
        }
        return ingestApi;
    }

    @Override
    public ControlApi control() {
        if (controlApi == null) {
            synchronized (StreamApiImpl.class) {
                if (controlApi == null) {
                    controlApi = new ControlApi();
                }
            }
        }
        return controlApi;
    }
}
