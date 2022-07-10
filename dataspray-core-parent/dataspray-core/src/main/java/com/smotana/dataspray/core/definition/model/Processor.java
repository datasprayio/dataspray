package com.smotana.dataspray.core.definition.model;

import com.google.common.collect.ImmutableSet;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@NonFinal
public class Processor {
    @Nonnull
    String name;

    ImmutableSet<StreamLink> inputs;

    ImmutableSet<StreamLink> outputs;

    @NonFinal
    transient Definition parent;

    public Processor setParent(Definition parent) {
        this.parent = parent;
        return this;
    }
}
