package com.smotana.dataspray.core.definition.model;

import com.google.common.collect.ImmutableSet;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@NonFinal
public class Store {
    @Nonnull
    String name;

    @Nonnull
    ImmutableSet<DataStream> streams;
}
