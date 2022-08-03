package io.dataspray.stream.client;

import lombok.NonNull;
import lombok.Value;

@Value
public class TestMessage<T> implements Message<T> {
    @NonNull
    String storeName;
    @NonNull
    String streamName;
    @NonNull
    T data;

    @Override
    public <N> Message<N> swapData(N newData) {
        return new TestMessage<N>(
                getStoreName(),
                getStreamName(),
                newData);
    }
}
