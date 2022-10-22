package io.dataspray.core.definition.model;

import com.google.common.collect.Sets;
import com.jcabi.aspects.Cacheable;
import io.dataspray.common.StringUtil;
import io.dataspray.runner.StoreType;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
public class StreamLink {
    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getUniqueNameCamelLower() {
        boolean allStreamNamesAreUnique = getParentProcessor().getStreams().stream()
                .map(StreamLink::getStreamName)
                .allMatch(Sets.newHashSet()::add);
        return allStreamNamesAreUnique
                ? StringUtil.camelCase(getStreamName(), false)
                : StringUtil.camelCase(getStoreName(), false) + StringUtil.camelCase(getStreamName(), false);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getUniqueNameCamelUpper() {
        String uniqueNameCamelLower = getUniqueNameCamelLower();
        return Character.toUpperCase(uniqueNameCamelLower.charAt(0)) + uniqueNameCamelLower.substring(1);
    }

    @Nonnull
    StoreType storeType;

    @Nonnull
    String storeName;

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public Store getStore() {
        return getParentDefinition().getStores().stream()
                .filter(store -> store.getName().equals(getStoreName()))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Store not found with name " + getStoreName()));
    }

    /**
     * Name of the specific stream from the store.
     *
     * Example: For KafkaStore, this would be the topic name.
     */
    @Nonnull
    String streamName;

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public DataStream getStream() {
        return getStore().getStreams().stream()
                .filter(stream -> stream.getName().equals(getStreamName()))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Stream not found with name " + getStreamName()));
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public DataFormat getDataFormat() {
        String dataFormatName = getStream().getDataFormatName();
        return getParentDefinition().getDataFormats().stream()
                .filter(dataFormat -> dataFormat.getName().equals(dataFormatName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Data format not found with name " + dataFormatName));
    }

    @Setter
    @NonFinal
    transient Definition parentDefinition;

    @Setter
    @NonFinal
    transient Processor parentProcessor;
}
