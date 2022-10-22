package io.dataspray.core.definition.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class JavaProcessor extends Processor {

    @Nonnull
    Target target;

    @Nullable
    String handler;

    @Getter
    @AllArgsConstructor
    public enum Target {
        DATASPRAY,
        SAMZA,
        FLINK
    }
}
