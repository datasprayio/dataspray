package io.dataspray.runner;

import lombok.Value;

@Value
public class MessageMetadata {
    StoreType storeType;
    String storeName;
    String streamName;
}
