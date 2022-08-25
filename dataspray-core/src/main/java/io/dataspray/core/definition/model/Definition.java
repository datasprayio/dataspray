package io.dataspray.core.definition.model;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gson.annotations.SerializedName;
import com.jcabi.aspects.Cacheable;
import io.dataspray.core.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.Optional;


/**
 * DataSpray definition containing resources and configuration
 */
@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class Definition extends Item {
    public static final int CACHEABLE_METHODS_LIFETIME_IN_MIN = 5;

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
        return StringUtil.javaPackageName(
                Strings.nullToEmpty(getNamespace())
                        + "."
                        + getName());
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getJavaPackagePath() {
        return Optional.ofNullable(Strings.emptyToNull(getJavaPackage()))
                .map(namespace -> namespace.replaceAll("\\.", File.separator) + File.separator)
                .orElse("");
    }

    @Nonnull
    ImmutableSet<DataFormat> dataFormats;

    ImmutableSet<KafkaStore> kafkaStores;

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public ImmutableSet<Store> getStores() {
        return ImmutableSet.<Store>builder()
                .addAll(kafkaStores)
                .build();
    }

    ImmutableSet<JavaProcessor> javaProcessors;

    public ImmutableSet<JavaProcessor> getJavaProcessors() {
        initialize();
        return javaProcessors;
    }

    @NonFinal
    transient boolean inited = false;

    private void initialize() {
        if (inited) {
            return;
        }
        inited = true;

        Streams.concat(
                Optional.ofNullable(javaProcessors).stream().flatMap(Collection::stream).map(processor -> (Processor) processor)
        ).forEach(processor -> {
            processor.setParent(this);
            processor.getStreams().forEach(stream -> {
                stream.setParentDefinition(this);
                stream.setParentProcessor(processor);
            });
        });
    }

    /**
     * Version of DataSpray definition
     */
    @Getter
    @AllArgsConstructor
    public enum Version {
        @SerializedName("V1.0.0")
        V_1_0_0
    }
}
