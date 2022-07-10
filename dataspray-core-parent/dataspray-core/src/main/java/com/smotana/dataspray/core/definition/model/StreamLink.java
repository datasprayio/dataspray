package com.smotana.dataspray.core.definition.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
public class StreamLink {
    @Nonnull
    String storeName;

    public Store getStore() {
        return parent.getStores().stream()
                .filter(store -> store.getName().equals(getStoreName()))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Store not found with name " + getStoreName()));
    }

    /**
     * Name of the specific stream from the store.
     *
     * Example: For KafkaStore, this would be the topic name.
     */
    @Nonnull
    String streamName;

    public DataStream getStream() {
        return getStore().getStreams().stream()
                .filter(stream -> stream.getName().equals(getStreamName()))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Stream not found with name " + getStreamName()));
    }

    public DataFormat getDataFormat() {
        String dataFormatName = getStream().getDataFormatName();
        return parent.getDataFormats().stream()
                .filter(dataFormat -> dataFormat.getName().equals(dataFormatName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Data format not found with name " + dataFormatName));
    }

    @NonFinal
    transient Definition parent;

    public StreamLink setParent(Definition parent) {
        this.parent = parent;
        return this;
    }
}
