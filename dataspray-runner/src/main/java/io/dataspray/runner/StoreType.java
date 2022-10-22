package io.dataspray.runner;

/**
 * Destination type. At this time only DataSpray available as destination, but future stores may include Kafka,
 * Kinesis, etc...
 */
public enum StoreType {
    DATASPRAY,
    KAFKA
}
