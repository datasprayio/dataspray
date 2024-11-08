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

import com.google.common.collect.Sets;
import com.jcabi.aspects.Cacheable;
import io.dataspray.common.StringUtil;
import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
public class StreamLink {
    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getUniqueNameCamelLower() {
        boolean allStreamNamesAreUnique = getParent().getStreams().stream()
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
        return getParent().getParent().getStores().stream()
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
        return getParent().getParent().getDataFormats().stream()
                .filter(dataFormat -> dataFormat.getName().equals(dataFormatName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Data format not found with name " + dataFormatName));
    }

    @Setter
    @NonFinal
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Processor parent;
}
