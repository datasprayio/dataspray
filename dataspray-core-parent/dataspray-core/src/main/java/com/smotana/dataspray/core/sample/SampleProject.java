package com.smotana.dataspray.core.sample;

import com.smotana.dataspray.core.definition.model.DataFormat;
import com.smotana.dataspray.core.definition.model.DataFormat.Serde;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition.DataSprayDefinitionBuilder;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition.Version;
import com.smotana.dataspray.core.definition.model.Input.InputBuilder;
import com.smotana.dataspray.core.definition.model.JavaProcessor;
import com.smotana.dataspray.core.definition.model.JavaProcessor.JavaProcessorBuilder;
import com.smotana.dataspray.core.definition.model.KafkaStore.KafkaStoreBuilder;
import com.smotana.dataspray.core.definition.model.Output.OutputBuilder;
import com.smotana.dataspray.core.definition.model.Topic;

import java.util.List;

public enum SampleProject {
    EMPTY(name -> new DataSprayDefinitionBuilder()
            .withName(name)
            .withVersion(Version.V_1_0_0)
            .build()),
    CLOUD(name -> new DataSprayDefinitionBuilder()
            .withName(name)
            .withVersion(Version.V_1_0_0)
            .withDataFormats(List.of(
                    new DataFormat.DataFormatBuilder()
                            .withName("register")
                            .withSerde(Serde.JSON)
                            .build(),
                    new DataFormat.DataFormatBuilder()
                            .withName("login")
                            .withSerde(Serde.JSON)
                            .build(),
                    new DataFormat.DataFormatBuilder()
                            .withName("ip")
                            .withSerde(Serde.JSON)
                            .build()))
            .withJavaProcessors(List.of(
                    new JavaProcessorBuilder()
                            .withDialect(JavaProcessor.Dialect.VANILLA)
                            .withInputs(List.of(
                                    new InputBuilder()
                                            .withDataFormatName("login")
                                            .withName("login")
                                            .build(),
                                    new InputBuilder()
                                            .withDataFormatName("register")
                                            .withName("register")
                                            .build()))
                            .withOutputs(List.of(
                                    new OutputBuilder()
                                            .withDataFormatName("ip")
                                            .withName("ip")
                                            .build()
                            ))
                            .withName("asdf")
                            .build()))
            .withKafkaStores(List.of(
                    new KafkaStoreBuilder()
                            .withBootstrapServers("localhost:12345")
                            .withTopics(List.of(
                                    new Topic.TopicBuilder()
                                            .withDataFormatName("login")
                                            .withTopicName("evt_login")
                                            .build(),
                                    new Topic.TopicBuilder()
                                            .withDataFormatName("register")
                                            .withTopicName("evt_register")
                                            .build(),
                                    new Topic.TopicBuilder()
                                            .withDataFormatName("ip")
                                            .withTopicName("last_ip")
                                            .build()))
                            .withName("myKafka")
                            .build()))
            .build());

    private final DefinitionCreator creator;

    SampleProject(DefinitionCreator creator) {
        this.creator = creator;
    }

    public DataSprayDefinition getDefinitionForName(String name) {
        return creator.create(name);
    }

    public interface DefinitionCreator {
        DataSprayDefinition create(String name);
    }
}
