package io.dataspray.runner;

import lombok.NonNull;
import lombok.Value;

@Value
public class TestMessage<T> implements Message<T> {
    @NonNull
    StoreType storeType;
    @NonNull
    String storeName;
    @NonNull
    String streamName;
    @NonNull
    T data;
}
