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
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

@Value
@SuperBuilder(toBuilder = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Endpoint extends Item {

    private static final ImmutableSet<DataFormat.Serde> REQUEST_SUPPORTED_DATA_FORMATS = ImmutableSet.copyOf(EnumSet
            // Exclude the following unsupported types
            .complementOf(EnumSet.of(
                    DataFormat.Serde.PROTOBUF,
                    DataFormat.Serde.AVRO)));
    private static final ImmutableSet<DataFormat.Serde> RESPONSE_SUPPORTED_DATA_FORMATS = ImmutableSet.copyOf(EnumSet
            // Exclude the following unsupported types
            .complementOf(EnumSet.of(
                    DataFormat.Serde.PROTOBUF,
                    DataFormat.Serde.AVRO)));

    @Nonnull
    HttpMethod method;

    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
    }

    /**
     * The path of the endpoint.
     * <p>
     * Any values in curly braces are considered path parameters.
     * E.g. /user/{userId}/profile
     */
    @Nonnull
    String path;

    public String getPath() {
        return path.startsWith("/") ? path : "/" + path;
    }

    public int getPathDepth() {
        return getPath().split("/").length;
    }

    public ImmutableList<RequiredPathDir> getPathRequiredDirs() {
        ImmutableList.Builder<RequiredPathDir> requiredPathDirsBuilder = ImmutableList.builder();
        String[] pathDirs = getPath().split("/");
        for (int i = 1; i < pathDirs.length; i++) {
            if (!pathDirs[i].startsWith("{")) {
                requiredPathDirsBuilder.add(RequiredPathDir.builder()
                        .index(i)
                        .name(pathDirs[i])
                        .build());
            }
        }
        return requiredPathDirsBuilder.build();
    }

    ImmutableSet<PathParameter> pathParams;

    public ImmutableSet<PathParameter> getPathParams() {
        return pathParams == null ? ImmutableSet.of() : pathParams;
    }

    public int getPathParamIndex(String pathParamName) {
        return Arrays.asList(getPath().split("/")).indexOf("{" + pathParamName + "}");
    }

    ImmutableSet<Parameter> queryParams;

    public ImmutableSet<Parameter> getQueryParams() {
        return queryParams == null ? ImmutableSet.of() : queryParams;
    }

    ImmutableSet<Parameter> headers;

    public ImmutableSet<Parameter> getHeaders() {
        return headers == null ? ImmutableSet.of() : headers;
    }

    ImmutableSet<Parameter> cookies;

    public ImmutableSet<Parameter> getCookies() {
        return cookies == null ? ImmutableSet.of() : cookies;
    }

    String requestDataFormatName;

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public DataFormat getRequestDataFormat() {
        if (requestDataFormatName == null) {
            return null;
        }
        return getParent().getParent().getParent().getDataFormats().stream()
                .filter(dataFormat -> dataFormat.getName().equals(getRequestDataFormatName()))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Request data format not found with name " + getRequestDataFormatName() + " for endpoint " + getPath()));
    }

    public Optional<DataFormat> getRequestDataFormatOpt() {
        return Optional.ofNullable(getRequestDataFormat());
    }

    ImmutableSet<String> contentTypes;

    public ImmutableSet<String> getContentTypes() {
        return contentTypes == null ? ImmutableSet.of() : contentTypes;
    }

    String responseDataFormatName;

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public DataFormat getResponseDataFormat() {
        if (responseDataFormatName == null) {
            return null;
        }
        return getParent().getParent().getParent().getDataFormats().stream()
                .filter(dataFormat -> dataFormat.getName().equals(getResponseDataFormatName()))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Response data format not found with name " + getResponseDataFormatName() + " for endpoint " + getPath()));
    }

    public Optional<DataFormat> getResponseDataFormatOpt() {
        return Optional.ofNullable(getResponseDataFormat());
    }

    public void initialize() {
        if (getRequestDataFormat() != null) {
            checkArgument(REQUEST_SUPPORTED_DATA_FORMATS.contains(getRequestDataFormat().getSerde()),
                    "Request data format %s is not supported by endpoint body under '%s'",
                    getRequestDataFormat().getSerde(), getName());
        }
        if (getResponseDataFormat() != null) {
            checkArgument(RESPONSE_SUPPORTED_DATA_FORMATS.contains(getResponseDataFormat().getSerde()),
                    "Response data format %s is not supported by endpoint body under '%s'",
                    getResponseDataFormat().getSerde(), getName());
        }
    }

    @Setter
    @NonFinal
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Web parent;
}
