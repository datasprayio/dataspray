package com.smotana.dataspray.core.definition.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.jcabi.aspects.Cacheable;
import com.smotana.dataspray.core.definition.model.DataFormat.Serde;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

import static com.smotana.dataspray.core.definition.model.Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@NonFinal
public class Processor extends Item {
    @Nonnull
    ImmutableSet<StreamLink> inputStreams;

    @Nonnull
    ImmutableSet<StreamLink> outputStreams;

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableList<StreamLink> getStreams() {
        return ImmutableList.<StreamLink>builder()
                .addAll(getInputStreams())
                .addAll(getOutputStreams())
                .build();
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getJsonStreams() {
        return getStreams(true, true, Serde.JSON);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getProtobufStreams() {
        return getStreams(true, true, Serde.PROTOBUF);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getAvroStreams() {
        return getStreams(true, true, Serde.AVRO);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getBinaryInputStreams() {
        return getStreams(true, false, Serde.BINARY);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getStringInputStreams() {
        return getStreams(true, false, Serde.STRING);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getGeneratedInputStreams() {
        return getStreams(true, false,
                Serde.JSON,
                Serde.PROTOBUF,
                Serde.AVRO);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getJsonInputStreams() {
        return getStreams(true, false, Serde.JSON);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getProtobufInputStreams() {
        return getStreams(true, false, Serde.PROTOBUF);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getAvroInputStreams() {
        return getStreams(true, false, Serde.AVRO);
    }

    private ImmutableSet<StreamLink> getStreams(boolean includeInputs, boolean includeOutputs, Serde... serdesArray) {
        List<ImmutableSet<StreamLink>> streamSets = Lists.newArrayList();
        ImmutableSet<Serde> serdes = ImmutableSet.copyOf(serdesArray);
        if (includeInputs) {
            streamSets.add(getInputStreams());
        }
        if (includeOutputs) {
            streamSets.add(getOutputStreams());
        }
        return streamSets.stream()
                .flatMap(Collection::stream)
                .filter(streamLink -> serdes.contains(streamLink.getDataFormat().getSerde()))
                .distinct()
                .collect(ImmutableSet.toImmutableSet());
    }

    @Setter
    @NonFinal
    transient Definition parent;
}
