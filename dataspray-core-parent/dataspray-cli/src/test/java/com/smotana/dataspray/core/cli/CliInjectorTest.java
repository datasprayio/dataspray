package com.smotana.dataspray.core.cli;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ServiceManager;
import com.google.gson.Gson;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.smotana.dataspray.core.CoreImpl;
import com.smotana.dataspray.core.definition.parser.DefinitionLoaderImpl;
import org.junit.Test;

class CliInjectorTest {

    @Test(timeout = 10_000L)
    public void testBindings() throws Exception {
        Injector injector = CliInjector.INSTANCE.create(Stage.TOOL);

        ImmutableSet.of(
                Cli.class,
                Install.class,
                Init.class,
                ServiceManager.class,
                Gson.class,
                DefinitionLoaderImpl.class,
                CoreImpl.class
        ).forEach(injector::getBinding);
    }
}