/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.core.definition.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.jcabi.aspects.Cacheable;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
@SuperBuilder(toBuilder = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NonFinal
@RegisterForReflection
public class Processor extends Item {
    @Nonnull
    Target target;

    public enum Target {
        DATASPRAY
        // TODO SAMZA
        // TODO FLINK
    }

    ImmutableSet<StreamLink> inputStreams;

    public ImmutableSet<StreamLink> getInputStreams() {
        return inputStreams == null ? ImmutableSet.of() : inputStreams;
    }

    public boolean hasInputStreams() {
        return !getInputStreams().isEmpty();
    }

    ImmutableSet<StreamLink> outputStreams;

    public ImmutableSet<StreamLink> getOutputStreams() {
        return outputStreams == null ? ImmutableSet.of() : outputStreams;
    }

    Web web;

    public Optional<Web> getWebOpt() {
        return Optional.ofNullable(web);
    }

    Boolean hasDynamoState;

    public boolean isHasDynamoState() {
        return Boolean.TRUE.equals(hasDynamoState);
    }

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

    /**
     * Checks whether a stream or a web request has a JSON data format.
     */
    public boolean hasJsonDataFormat() {
        return getStreams().stream()
                       .anyMatch(streamLink -> streamLink.getDataFormat().getSerde() == DataFormat.Serde.JSON)
               || getWebOpt()
                       .stream()
                       .flatMap(w -> w.getEndpoints().stream())
                       .anyMatch(e -> e.getRequestDataFormat() != null
                                      && e.getRequestDataFormat().getSerde() == DataFormat.Serde.JSON);
    }

    /**
     * Gets all DataFormats across web requests and streams.
     */
    public ImmutableSet<DataFormat> getDataFormats() {
        return ImmutableSet.<DataFormat>builder()
                .addAll(getStreams().stream()
                        .map(StreamLink::getDataFormat)
                        .collect(Collectors.toSet()))
                .addAll(getWebOpt()
                        .stream()
                        .flatMap(w -> w.getEndpoints().stream())
                        .flatMap(endpoint -> Stream.of(
                                endpoint.getRequestDataFormatOpt(),
                                endpoint.getResponseDataFormatOpt()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet()))
                .build();
    }

    public void initialize() {
        // To be overriden by subclasses
    }

    @Setter
    @NonFinal
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Definition parent;
}
