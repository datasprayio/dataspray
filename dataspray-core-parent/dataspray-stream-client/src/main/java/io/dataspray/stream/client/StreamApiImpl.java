package io.dataspray.stream.client;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.ingest.client.IngestApi;

@Singleton
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

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(StreamApi.class).to(StreamApiImpl.class).asEagerSingleton();
            }
        };
    }
}
