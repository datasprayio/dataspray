package com.smotana.dataspray.core.generator;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.junit.Test;

class GeneratorInjectorTest {

    @Test(timeout = 10_000L)
    public void testBindings() throws Exception {
        Injector injector = GeneratorInjector.INSTANCE.create(Stage.TOOL);

        ImmutableSet.of(
                YamlToJsonSchema.class,
                DefinitionGenerator.class
        ).forEach(injector::getBinding);
    }
}