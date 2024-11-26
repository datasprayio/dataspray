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

package io.dataspray.core.sample;

import com.google.common.collect.ImmutableSet;
import io.dataspray.core.definition.model.Cors;
import io.dataspray.core.definition.model.DataFormat;
import io.dataspray.core.definition.model.DataFormat.Serde;
import io.dataspray.core.definition.model.DataStream;
import io.dataspray.core.definition.model.DatasprayStore;
import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.model.DynamoState;
import io.dataspray.core.definition.model.Endpoint;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Parameter;
import io.dataspray.core.definition.model.PathParameter;
import io.dataspray.core.definition.model.StoreType;
import io.dataspray.core.definition.model.StreamLink;
import io.dataspray.core.definition.model.TypescriptProcessor;
import io.dataspray.core.definition.model.Web;

import java.util.Set;

public enum SampleProject {
    EMPTY(name -> Definition.builder()
            .name(name)
            .version(Definition.Version.V_0_0_0)
            .dataFormats(ImmutableSet.of())
            .build()
            .initialize()),
    JAVA(name -> Definition.builder()
            .name(name)
            .namespace("com.example")
            .version(Definition.Version.V_0_0_0)
            .dataFormats(ImmutableSet.of(
                    DataFormat.builder()
                            .name("register")
                            .serde(Serde.JSON)
                            .build(),
                    DataFormat.builder()
                            .name("login")
                            .serde(Serde.PROTOBUF)
                            .build(),
                    DataFormat.builder()
                            .name("ip")
                            .serde(Serde.AVRO)
                            .build()))
            .javaProcessors(ImmutableSet.of(
                    JavaProcessor.builder()
                            .name("IP Extractor")
                            .target(JavaProcessor.Target.DATASPRAY)
                            .inputStreams(ImmutableSet.of(
                                    StreamLink.builder()
                                            .storeType(StoreType.DATASPRAY)
                                            .storeName("default")
                                            .streamName("evt_login")
                                            .build(),
                                    StreamLink.builder()
                                            .storeType(StoreType.DATASPRAY)
                                            .storeName("default")
                                            .streamName("evt_register")
                                            .build()))
                            .outputStreams(ImmutableSet.of(
                                    StreamLink.builder()
                                            .storeType(StoreType.DATASPRAY)
                                            .storeName("default")
                                            .streamName("last_ip")
                                            .build()))
                            .web(Web.builder()
                                    .isPublic(true)
                                    .cors(Cors.builder()
                                            .allowOrigins(Set.of("example.com"))
                                            .allowMethods(Set.of("GET", "POST"))
                                            .allowHeaders(Set.of("Authorization"))
                                            .build())
                                    .endpoints(ImmutableSet.of(
                                            Endpoint.builder()
                                                    .name("receiveRegistration")
                                                    .method(Endpoint.HttpMethod.POST)
                                                    .path("/user/{userId}/ip")
                                                    .pathParams(ImmutableSet.of(PathParameter.builder()
                                                            .name("userId")
                                                            .build()))
                                                    .queryParams(ImmutableSet.of(
                                                            Parameter.builder().name("limit").isRequired(true).build(),
                                                            Parameter.builder().name("cursor").isRequired(false).build()))
                                                    .cookies(ImmutableSet.of(
                                                            Parameter.builder().name("session").isRequired(true).build()))
                                                    .contentTypes(ImmutableSet.of("application/json"))
                                                    .requestDataFormatName("register")
                                                    .responseDataFormatName("register")
                                                    .headers(ImmutableSet.of(
                                                            Parameter.builder().name("Authorization").isRequired(true).build()))
                                                    .build()))
                                    .build())
                            .hasDynamoState(true)
                            .includeSingleTableLibrary(true)
                            .build()))
            .datasprayStores(ImmutableSet.of(
                    DatasprayStore.builder()
                            .name("default")
                            .streams(ImmutableSet.of(
                                    DataStream.builder()
                                            .dataFormatName("login")
                                            .name("evt_login")
                                            .build(),
                                    DataStream.builder()
                                            .dataFormatName("register")
                                            .name("evt_register")
                                            .build(),
                                    DataStream.builder()
                                            .dataFormatName("ip")
                                            .name("last_ip")
                                            .build()))
                            .build()))
            .dynamoState(DynamoState.builder()
                    .lsiCount(1L)
                    .gsiCount(0L)
                    .build())
            .build()
            .initialize()),
    TYPESCRIPT(name -> Definition.builder()
            .name(name)
            .namespace("com.example")
            .version(Definition.Version.V_0_0_0)
            .dataFormats(ImmutableSet.of(
                    DataFormat.builder()
                            .name("register")
                            .serde(Serde.JSON)
                            .build(),
                    DataFormat.builder()
                            .name("login")
                            .serde(Serde.JSON)
                            .build(),
                    DataFormat.builder()
                            .name("ip")
                            .serde(Serde.JSON)
                            .build()))
            .typescriptProcessors(ImmutableSet.of(
                    TypescriptProcessor.builder()
                            .name("IP Extractor")
                            .target(JavaProcessor.Target.DATASPRAY)
                            .inputStreams(ImmutableSet.of(
                                    StreamLink.builder()
                                            .storeType(StoreType.DATASPRAY)
                                            .storeName("default")
                                            .streamName("evt_login")
                                            .build(),
                                    StreamLink.builder()
                                            .storeType(StoreType.DATASPRAY)
                                            .storeName("default")
                                            .streamName("evt_register")
                                            .build()))
                            .outputStreams(ImmutableSet.of(
                                    StreamLink.builder()
                                            .storeType(StoreType.DATASPRAY)
                                            .storeName("default")
                                            .streamName("last_ip")
                                            .build()))
                            .web(Web.builder()
                                    .isPublic(true)
                                    .cors(Cors.builder()
                                            .allowOrigins(Set.of("example.com"))
                                            .allowMethods(Set.of("GET", "POST"))
                                            .allowHeaders(Set.of("Authorization"))
                                            .build())
                                    .endpoints(ImmutableSet.of(
                                            Endpoint.builder()
                                                    .name("receiveRegistration")
                                                    .method(Endpoint.HttpMethod.POST)
                                                    .path("/user/{userId}/ip")
                                                    .pathParams(ImmutableSet.of(PathParameter.builder()
                                                            .name("userId")
                                                            .build()))
                                                    .queryParams(ImmutableSet.of(
                                                            Parameter.builder().name("limit").isRequired(true).build(),
                                                            Parameter.builder().name("cursor").isRequired(false).build()))
                                                    .cookies(ImmutableSet.of(
                                                            Parameter.builder().name("session").isRequired(true).build()))
                                                    .contentTypes(ImmutableSet.of("application/json"))
                                                    .requestDataFormatName("register")
                                                    .responseDataFormatName("register")
                                                    .headers(ImmutableSet.of(
                                                            Parameter.builder().name("Authorization").isRequired(true).build()))
                                                    .build()))
                                    .build())
                            .hasDynamoState(true)
                            .build()))
            .datasprayStores(ImmutableSet.of(
                    DatasprayStore.builder()
                            .name("default")
                            .streams(ImmutableSet.of(
                                    DataStream.builder()
                                            .dataFormatName("login")
                                            .name("evt_login")
                                            .build(),
                                    DataStream.builder()
                                            .dataFormatName("register")
                                            .name("evt_register")
                                            .build(),
                                    DataStream.builder()
                                            .dataFormatName("ip")
                                            .name("last_ip")
                                            .build()))
                            .build()))
            .dynamoState(DynamoState.builder()
                    .lsiCount(1L)
                    .gsiCount(0L)
                    .build())
            .build()
            .initialize());

    private final DefinitionCreator creator;

    SampleProject(DefinitionCreator creator) {
        this.creator = creator;
    }

    public Definition getDefinitionForName(String name) {
        return creator.create(name);
    }

    public interface DefinitionCreator {
        Definition create(String name);
    }
}
