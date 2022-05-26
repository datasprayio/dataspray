// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package com.smotana.dataspray.core.generator;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.smotana.dataspray.core.common.json.GsonProvider;
import com.smotana.dataspray.core.common.json.JacksonProvider;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public enum GeneratorInjector {
    INSTANCE;

    private static volatile Injector injector = null;

    public Injector get() {
        if (injector == null) {
            synchronized (GeneratorInjector.class) {
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
                install(YamlToJsonSchema.module());
                install(DefinitionGenerator.module());

                install(GsonProvider.module());
                install(JacksonProvider.module());
            }
        };
    }
}
