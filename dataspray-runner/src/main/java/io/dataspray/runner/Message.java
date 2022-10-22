package io.dataspray.runner;

public interface Message<T> {

    StoreType getStoreType();

    String getStoreName();

    String getStreamName();

    T getData();
}
