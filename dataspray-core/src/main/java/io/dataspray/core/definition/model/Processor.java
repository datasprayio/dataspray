package io.dataspray.core.definition.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.jcabi.aspects.Cacheable;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@NonFinal
public class Processor extends Item {
    @Nonnull
    ImmutableSet<StreamLink> inputStreams;

    @Nonnull
    ImmutableSet<StreamLink> outputStreams;

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableList<StreamLink> getStreams() {
        return ImmutableList.<StreamLink>builder()
                .addAll(getInputStreams())
                .addAll(getOutputStreams())
                .build();
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getJsonStreams() {
        return getStreams(true, true, DataFormat.Serde.JSON);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getProtobufStreams() {
        return getStreams(true, true, DataFormat.Serde.PROTOBUF);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getAvroStreams() {
        return getStreams(true, true, DataFormat.Serde.AVRO);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getBinaryInputStreams() {
        return getStreams(true, false, DataFormat.Serde.BINARY);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getStringInputStreams() {
        return getStreams(true, false, DataFormat.Serde.STRING);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getGeneratedInputStreams() {
        return getStreams(true, false,
                DataFormat.Serde.JSON,
                DataFormat.Serde.PROTOBUF,
                DataFormat.Serde.AVRO);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getJsonInputStreams() {
        return getStreams(true, false, DataFormat.Serde.JSON);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getProtobufInputStreams() {
        return getStreams(true, false, DataFormat.Serde.PROTOBUF);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getAvroInputStreams() {
        return getStreams(true, false, DataFormat.Serde.AVRO);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getBinaryOutputStreams() {
        return getStreams(false, true, DataFormat.Serde.BINARY);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getStringOutputStreams() {
        return getStreams(false, true, DataFormat.Serde.STRING);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getGeneratedOutputStreams() {
        return getStreams(false, true,
                DataFormat.Serde.JSON,
                DataFormat.Serde.PROTOBUF,
                DataFormat.Serde.AVRO);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getJsonOutputStreams() {
        return getStreams(false, true, DataFormat.Serde.JSON);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getProtobufOutputStreams() {
        return getStreams(false, true, DataFormat.Serde.PROTOBUF);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<StreamLink> getAvroOutputStreams() {
        return getStreams(false, true, DataFormat.Serde.AVRO);
    }

    private ImmutableSet<StreamLink> getStreams(boolean includeInputs, boolean includeOutputs, DataFormat.Serde... serdesArray) {
        List<ImmutableSet<StreamLink>> streamSets = Lists.newArrayList();
        ImmutableSet<DataFormat.Serde> serdes = ImmutableSet.copyOf(serdesArray);
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
