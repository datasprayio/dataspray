package io.dataspray.core.sample;

import com.google.common.collect.ImmutableSet;
import io.dataspray.core.definition.model.DataFormat;
import io.dataspray.core.definition.model.DataFormat.Serde;
import io.dataspray.core.definition.model.DataSprayStore;
import io.dataspray.core.definition.model.DataStream;
import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.KafkaStore;
import io.dataspray.core.definition.model.StreamLink;
import io.dataspray.runner.StoreType;

import static io.dataspray.runner.RawCoordinatorImpl.DATASPRAY_DEFAULT_STORE_NAME;

public enum SampleProject {
    EMPTY(name -> Definition.builder()
            .name(name)
            .version(Definition.Version.V_1_0_0)
            .dataFormats(ImmutableSet.of())
            .build()),
    CLOUD(name -> Definition.builder()
            .name(name)
            .namespace("com.example")
            .version(Definition.Version.V_1_0_0)
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
                                            .storeName(DATASPRAY_DEFAULT_STORE_NAME)
                                            .streamName("evt_login")
                                            .build(),
                                    StreamLink.builder()
                                            .storeType(StoreType.DATASPRAY)
                                            .storeName(DATASPRAY_DEFAULT_STORE_NAME)
                                            .streamName("evt_register")
                                            .build()))
                            .outputStreams(ImmutableSet.of(
                                    StreamLink.builder()
                                            .storeType(StoreType.DATASPRAY)
                                            .storeName(DATASPRAY_DEFAULT_STORE_NAME)
                                            .streamName("last_ip")
                                            .build()))
                            .build()))
            .dataSprayStores(ImmutableSet.of(
                    DataSprayStore.builder()
                            .name(DATASPRAY_DEFAULT_STORE_NAME)
                            .customerId(DATASPRAY_DEFAULT_STORE_NAME)
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
            .build()),
    KAFKA(name -> Definition.builder()
            .name(name)
            .namespace("com.example")
            .version(Definition.Version.V_1_0_0)
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
                                            .storeType(StoreType.KAFKA)
                                            .storeName("myKafka")
                                            .streamName("evt_login")
                                            .build(),
                                    StreamLink.builder()
                                            .storeType(StoreType.KAFKA)
                                            .storeName("myKafka")
                                            .streamName("evt_register")
                                            .build()))
                            .outputStreams(ImmutableSet.of(
                                    StreamLink.builder()
                                            .storeType(StoreType.KAFKA)
                                            .storeName("myKafka")
                                            .streamName("last_ip")
                                            .build()))
                            .build()))
            .kafkaStores(ImmutableSet.of(
                    KafkaStore.builder()
                            .name("myKafka")
                            .bootstrapServers("localhost:12345")
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
            .build());

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
