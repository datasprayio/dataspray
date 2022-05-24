package com.smotana.dataspray.core.sample;

import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition.DataSprayDefinitionBuilder;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition.Version;
import com.smotana.dataspray.core.definition.model.JavaProcessor.JavaProcessorBuilder;
import com.smotana.dataspray.core.definition.model.KafkaStore.KafkaStoreBuilder;

import java.util.List;

public enum SampleProject {
    EMPTY(new DataSprayDefinitionBuilder()
            .withVersion(Version.V_1_0_0)
            .build()),
    CLOUD(new DataSprayDefinitionBuilder()
            .withVersion(Version.V_1_0_0)
            .withSamzaProcessors(List.of(
                    new JavaProcessorBuilder()
                            .withName("asdf")
                            .build()))
            .withKafkaStores(List.of(
                    new KafkaStoreBuilder()
                            .withName("myKafka")
                            .build()))
            .build());

    private final DataSprayDefinition definition;

    SampleProject(DataSprayDefinition definition) {
        this.definition = definition;
    }

    public DataSprayDefinition getDefinition() {
        return definition;
    }
}
