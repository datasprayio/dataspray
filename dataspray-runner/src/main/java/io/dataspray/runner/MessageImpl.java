package io.dataspray.runner;

public class MessageImpl<T> implements Message<T> {
    private final MessageMetadata metadata;
    private final T data;

    public MessageImpl(MessageMetadata metadata, T data) {
        this.metadata = metadata;
        this.data = data;
    }

    @Override
    public StoreType getStoreType() {
        return metadata.getStoreType();
    }

    @Override
    public String getStoreName() {
        return metadata.getStoreName();
    }

    @Override
    public String getStreamName() {
        return metadata.getStreamName();
    }

    @Override
    public T getData() {
        return data;
    }
}
