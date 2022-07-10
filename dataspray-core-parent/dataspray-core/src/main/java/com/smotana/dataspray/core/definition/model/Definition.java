package com.smotana.dataspray.core.definition.model;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Optional;


/**
 * DataSpray definition containing resources and configuration
 */
@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
public class Definition {
    /**
     * Version of DataSpray definition
     */
    @Nonnull
    Version version;

    /**
     * Project name
     * (Required)
     */
    @Nonnull
    String name;

    /**
     * Project wide namespace; for Java used as package name
     */
    String namespace;

    public String getJavaPackagePath() {
        return Optional.ofNullable(Strings.emptyToNull(getNamespace()))
                .map(namespace -> namespace.replaceAll("\\.", File.separator) + File.separator)
                .orElse("");
    }

    ImmutableSet<DataFormat> dataFormats;

    ImmutableSet<KafkaStore> kafkaStores;

    public ImmutableSet<Store> getStores() {
        return ImmutableSet.<Store>builder()
                .addAll(kafkaStores)
                .build();
    }

    ImmutableSet<JavaProcessor> javaProcessors;

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
