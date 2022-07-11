package io.dataspray.runner;

public interface Message<T> {

    String getStoreName();

    String getStreamName();

    T getData();

    <N> Message<N> swapData(N newData);
}
