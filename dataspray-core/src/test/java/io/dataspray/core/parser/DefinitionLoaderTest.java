package io.dataspray.core.parser;

import com.google.inject.Inject;
import io.dataspray.core.CoreAbstractTest;
import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.parser.DefinitionLoader;
import io.dataspray.core.definition.parser.DefinitionLoaderImpl;
import io.dataspray.core.sample.SampleProject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        Assertions.assertEquals(definition, loader.fromYaml(loader.toYaml(definition)));
        Assertions.assertEquals(definition, loader.fromJson(loader.toJson(definition, true)));
        Assertions.assertEquals(definition, loader.fromJson(loader.toJson(definition, false)));
    }

    private Definition.DefinitionBuilder<?, ?> mockDefinition() {
        return SampleProject.CLOUD.getDefinitionForName("test").toBuilder();
    }
}