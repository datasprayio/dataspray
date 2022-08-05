// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.core.cli;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import io.dataspray.core.BuilderImpl;
import io.dataspray.core.CodegenImpl;
import io.dataspray.core.CoreImpl;
import io.dataspray.core.GitExcludeFileTracker;
import io.dataspray.core.RuntimeImpl;
import io.dataspray.core.common.json.GsonProvider;
import io.dataspray.core.common.json.JacksonProvider;
import io.dataspray.core.definition.parser.DefinitionLoaderImpl;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public enum CliInjector {
    INSTANCE;

    private static volatile Injector injector = null;

    public Injector get() {
        if (injector == null) {
            synchronized (CliInjector.class) {
                if (injector == null) {
                    injector = create(Stage.DEVELOPMENT);
                }
            }
        }
        return injector;
    }

    @VisibleForTesting
    protected Injector create(Stage stage) {
        return Guice.createInjector(stage, module());
    }

    private static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                // CLI commands
                install(Cli.module());
                install(Init.module());
                install(Install.module());
                install(Status.module());
                install(Deploy.module());

                install(CoreImpl.module());
                install(CodegenImpl.module());
                install(BuilderImpl.module(true));
                install(RuntimeImpl.module());

                install(GitExcludeFileTracker.module());
                install(DefinitionLoaderImpl.module());

                install(GsonProvider.module());
                install(JacksonProvider.module());
            }
        };
    }
}
