package com.smotana.dataspray.core.definition.parser;

import com.google.inject.Inject;
import com.smotana.dataspray.core.CliAbstractTest;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition.DataSprayDefinitionBuilder;
import com.smotana.dataspray.core.definition.model.JavaProcessor;
import com.smotana.dataspray.core.definition.model.KafkaStore;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
public class DefinitionLoaderTest extends CliAbstractTest {

    @Inject
    private DefinitionLoader loader;

    @Override
    protected void configure() {
        super.configure();

        install(DefinitionLoaderImpl.module());
        install(DefinitionValidatorImpl.module());
    }

    @Test
    public void testSerde() throws Exception {
        DataSprayDefinition definition = mockDefinition().build();
        log.info("Yaml:\n{}", loader.toYaml(definition));
        log.info("Json:\n{}", loader.toJson(definition, false));
        log.info("Json pretty:\n{}", loader.toJson(definition, true));
        assertEquals(definition, loader.fromYaml(loader.toYaml(definition)));
        assertEquals(definition, loader.fromJson(loader.toJson(definition, true)));
        assertEquals(definition, loader.fromJson(loader.toJson(definition, false)));
    }

    private DataSprayDefinitionBuilder mockDefinition() {
        return (DataSprayDefinitionBuilder) new DataSprayDefinitionBuilder()
                .withVersion(DataSprayDefinition.Version.V_1_0_0)
                .withJavaProcessors(List.of(
                        new JavaProcessor.JavaProcessorBuilder()
                                .withName("asdf")
                                .build()))
                .withKafkaStores(List.of(
                        new KafkaStore.KafkaStoreBuilder()
                                .withName("myKafka")
                                .build()));
    }
}