package com.smotana.dataspray.core.definition.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
public class DataFormat {
    @Nonnull
    String name;

    @Nonnull
    DataFormat.Serde serde;

    @Getter
    @AllArgsConstructor
    public enum Serde {
        BINARY,
        STRING,
        NUMBER,
        JSON,
        PROTOBUF,
        AVRO
    }
}
