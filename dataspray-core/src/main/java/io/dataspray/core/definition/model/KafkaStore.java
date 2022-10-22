package io.dataspray.core.definition.model;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class KafkaStore extends Store {
    /**
     * A list of network endpoints where the Kafka brokers are running. This is given as a comma-separated list of
     * hostname:port pairs. It's not necessary to list every single Kafka node in the cluster.
     */
    @Nonnull
    String bootstrapServers;
}
