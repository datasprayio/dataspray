package io.dataspray.core.definition.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class JavaProcessor extends Processor {

    Target target;

    @Getter
    @AllArgsConstructor
    public enum Target {
        DATASPRAY,
        SAMZA,
        FLINK
    }
}
