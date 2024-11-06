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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.SerializedName;
import com.jcabi.aspects.Cacheable;
import io.dataspray.common.StringUtil;
import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.io.File;
import java.util.Optional;


/**
 * DataSpray definition containing resources and configuration
 */
@Value
@SuperBuilder(toBuilder = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Definition extends Item {
    public static final int CACHEABLE_METHODS_LIFETIME_IN_MIN = 5;

    @Builder.Default
    String $schema = "https://dataspray.io/project/schema.json";

    /**
     * Version of DataSpray definition
     */
    @Nonnull
    Version version;

    /**
     * Project wide namespace; for Java used as package name
     */
    String namespace;

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getJavaPackage() {
        return StringUtil.javaPackageName(Strings.nullToEmpty(getNamespace()));
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getJavaPackagePath() {
        return Optional.ofNullable(Strings.emptyToNull(getJavaPackage()))
                .map(namespace -> namespace.replaceAll("\\.", File.separator) + File.separator)
                .orElse("");
    }

    @Nonnull
    ImmutableSet<DataFormat> dataFormats;

    @Builder.Default
    ImmutableSet<DatasprayStore> datasprayStores = ImmutableSet.of();

    @Builder.Default
    ImmutableSet<KafkaStore> kafkaStores = ImmutableSet.of();

    DynamoState dynamoState;

    public Optional<DynamoState> getDynamoState() {
        return Optional.ofNullable(dynamoState);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<Store> getStores() {
        return ImmutableSet.<Store>builder()
                .addAll(datasprayStores)
                .addAll(kafkaStores)
                .build();
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<Processor> getProcessors() {
        return ImmutableSet.<Processor>builder()
                .addAll(javaProcessors)
                .addAll(typescriptProcessors)
                .build();
    }

    @Builder.Default
    ImmutableSet<JavaProcessor> javaProcessors = ImmutableSet.of();
    @Builder.Default
    ImmutableSet<TypescriptProcessor> typescriptProcessors = ImmutableSet.of();

    @Nonnull
    public ImmutableSet<JavaProcessor> getJavaProcessors() {
        return javaProcessors == null ? ImmutableSet.of() : javaProcessors;
    }

    @Nonnull
    public ImmutableSet<TypescriptProcessor> getTypescriptProcessors() {
        return typescriptProcessors == null ? ImmutableSet.of() : typescriptProcessors;
    }

    public Definition initialize() {
        getProcessors().forEach(processor -> {
            processor.setParent(this);
            processor.getStreams().forEach(stream -> {
                stream.setParent(processor);
            });
            processor.getEndpoint().ifPresent(endpoint -> {
                endpoint.setParent(processor);
            });
            processor.initialize();
        });
        return this;
    }

    /**
     * Version of DataSpray definition
     */
    @Getter
    @AllArgsConstructor
    public enum Version {
        @SerializedName("V0.0.0")
        V_0_0_0
    }
}
