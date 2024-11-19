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
import com.jcabi.aspects.Cacheable;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

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

    /**
     * Gets all DataFormats across web requests and streams.
     */
    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<DataFormat> getDataFormats() {
        return ImmutableSet.<DataFormat>builder()
                .addAll(getStreams().stream()
                        .map(StreamLink::getDataFormat)
                        .collect(Collectors.toSet()))
                .addAll(getWebOpt()
                        .stream()
                        .flatMap(w -> w.getEndpoints().stream())
                        .flatMap(e -> Stream.of(
                                e.getRequestDataFormatOpt(),
                                e.getResponseDataFormatOpt()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet()))
                .build();
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<DataFormat> getJsonDataFormats() {
        return getDataFormats().stream()
                .filter(d -> d.getSerde() == DataFormat.Serde.JSON)
                .collect(ImmutableSet.toImmutableSet());
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<DataFormat> getAvroDataFormats() {
        return getDataFormats().stream()
                .filter(d -> d.getSerde() == DataFormat.Serde.AVRO)
                .collect(ImmutableSet.toImmutableSet());
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<DataFormat> getProtobufDataFormats() {
        return getDataFormats().stream()
                .filter(d -> d.getSerde() == DataFormat.Serde.PROTOBUF)
                .collect(ImmutableSet.toImmutableSet());
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
