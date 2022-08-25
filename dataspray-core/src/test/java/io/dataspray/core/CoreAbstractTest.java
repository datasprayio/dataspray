// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.core;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.google.inject.util.Modules;
import io.dataspray.core.common.json.GsonProvider;
import io.dataspray.core.common.json.JacksonProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;

@Slf4j
public abstract class CoreAbstractTest extends AbstractModule {

    @Inject
    protected Injector injector;

    @BeforeEach
    public void setup() throws Exception {
        injector = Guice.createInjector(Stage.DEVELOPMENT, new AbstractModule() {
            @Override
            protected void configure() {
                install(Modules.override(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                install(GsonProvider.module());
                                install(JacksonProvider.module());
                            }
                        }
                ).with(CoreAbstractTest.this));
            }
        });
        injector.injectMembers(this);
    }

    protected void configure() {
        super.configure();
    }
}
