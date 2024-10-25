/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.core.parser;

import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.parser.DefinitionLoader;
import io.dataspray.core.sample.SampleProject;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Slf4j
@QuarkusTest
public class DefinitionLoaderTest {

    @Inject
    DefinitionLoader loader;

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = SampleProject.class, mode = EnumSource.Mode.INCLUDE, names = {"JAVA", "TYPESCRIPT"})
    public void testSerde(SampleProject sampleProject) throws Exception {
        Definition definition = sampleProject.getDefinitionForName("test");
        log.info("Yaml:\n{}", loader.toYaml(definition));
        log.info("Json:\n{}", loader.toJson(definition, false));
        log.info("Json pretty:\n{}", loader.toJson(definition, true));
        Assertions.assertEquals(definition, loader.fromYaml(loader.toYaml(definition)));
        Assertions.assertEquals(definition, loader.fromJson(loader.toJson(definition, true)));
        Assertions.assertEquals(definition, loader.fromJson(loader.toJson(definition, false)));
    }
}