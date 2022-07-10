package com.smotana.dataspray.core.definition.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
public class DataStream {
    @Nonnull
    String name;

    @Nonnull
    String dataFormatName;
}
