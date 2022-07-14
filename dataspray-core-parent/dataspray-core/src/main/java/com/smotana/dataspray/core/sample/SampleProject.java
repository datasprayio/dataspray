package com.smotana.dataspray.core.sample;

import com.google.common.collect.ImmutableSet;
import com.smotana.dataspray.core.definition.model.DataFormat;
import com.smotana.dataspray.core.definition.model.DataFormat.Serde;
import com.smotana.dataspray.core.definition.model.DataStream;
import com.smotana.dataspray.core.definition.model.Definition;
import com.smotana.dataspray.core.definition.model.Definition.Version;
import com.smotana.dataspray.core.definition.model.JavaProcessor;
import com.smotana.dataspray.core.definition.model.KafkaStore;
import com.smotana.dataspray.core.definition.model.StreamLink;

public enum SampleProject {
    EMPTY(name -> Definition.builder()
            .name(name)
            .version(Version.V_1_0_0)
            .dataFormats(ImmutableSet.of())
            .build()),
    CLOUD(name -> Definition.builder()
            .name(name)
            .namespace("com.example")
            .version(Version.V_1_0_0)
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
                            .inputStreams(ImmutableSet.of(
                                    StreamLink.builder()
                                            .storeName("myKafka")
                                            .streamName("evt_login")
                                            .build(),
                                    StreamLink.builder()
                                            .storeName("myKafka")
                                            .streamName("evt_register")
                                            .build()))
                            .outputStreams(ImmutableSet.of(
                                    StreamLink.builder()
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
