package io.dataspray.core.definition.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class DataFormat extends Item {

    @Nonnull
    DataFormat.Serde serde;

    public boolean isSerdeBinary() {
        return Serde.BINARY.equals(serde);
    }

    public boolean isSerdeString() {
        return Serde.STRING.equals(serde);
    }

    public boolean isSerdeJson() {
        return Serde.JSON.equals(serde);
    }

    public boolean isSerdeProtobuf() {
        return Serde.PROTOBUF.equals(serde);
    }

    public boolean isSerdeAvro() {
        return Serde.AVRO.equals(serde);
    }

    @Getter
    @AllArgsConstructor
    public enum Serde {
        BINARY,
        STRING,
        JSON,
        PROTOBUF,
        AVRO
    }
}
