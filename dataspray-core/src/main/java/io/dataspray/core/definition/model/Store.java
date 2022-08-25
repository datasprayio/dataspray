package io.dataspray.core.definition.model;

import com.google.common.collect.ImmutableSet;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class Store extends Item {
    @Nonnull
    ImmutableSet<DataStream> streams;
}
