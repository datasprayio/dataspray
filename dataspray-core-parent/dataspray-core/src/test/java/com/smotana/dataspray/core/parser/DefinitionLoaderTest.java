package com.smotana.dataspray.core.parser;

import com.google.inject.Inject;
import com.smotana.dataspray.core.CoreAbstractTest;
import com.smotana.dataspray.core.definition.model.Definition;
import com.smotana.dataspray.core.definition.parser.DefinitionLoader;
import com.smotana.dataspray.core.definition.parser.DefinitionLoaderImpl;
import com.smotana.dataspray.core.sample.SampleProject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class DefinitionLoaderTest extends CoreAbstractTest {

    @Inject
    private DefinitionLoader loader;

    @Override
    protected void configure() {
        super.configure();

        install(DefinitionLoaderImpl.module());
    }

    @Test
    public void testSerde() throws Exception {
        Definition definition = mockDefinition().build();
        log.info("Yaml:\n{}", loader.toYaml(definition));
        log.info("Json:\n{}", loader.toJson(definition, false));
        log.info("Json pretty:\n{}", loader.toJson(definition, true));
        assertEquals(definition, loader.fromYaml(loader.toYaml(definition)));
        assertEquals(definition, loader.fromJson(loader.toJson(definition, true)));
        assertEquals(definition, loader.fromJson(loader.toJson(definition, false)));
    }

    private Definition.DefinitionBuilder<?, ?> mockDefinition() {
        return SampleProject.CLOUD.getDefinitionForName("test").toBuilder();
    }
}