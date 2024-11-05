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

package io.dataspray.core.definition.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.dataspray.core.definition.model.Definition;

import java.nio.file.Path;
import java.util.Optional;

public class JsonSchemaGenerator {

    public static void main(String... args) throws Exception {
        if (args.length != 1) {
            throw new Exception("Invalid number of arguments");
        }

        Path outputFolder = Path.of(args[0]);
        outputFolder.toFile().mkdirs();

        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON);

        // Notes on using the library with Gson:
        // https://github.com/victools/jsonschema-generator/issues/218#issuecomment-997261368
        // https://github.com/victools/jsonschema-generator/pull/448
        configBuilder.forFields().withIgnoreCheck(field -> {
            Expose b = field.getAnnotationConsideringFieldAndGetter(Expose.class);
            return b != null && !b.serialize();
        });
        configBuilder.forFields().withPropertyNameOverrideResolver(
                field -> Optional.ofNullable(field.getAnnotationConsideringFieldAndGetter(SerializedName.class))
                        .map(SerializedName::value).orElse(null));
        configBuilder.forTypesInGeneral().withCustomDefinitionProvider(new GsonCustomEnumDefinitionProvider());
        configBuilder.with(new JacksonModule());

        ObjectNode schema = new SchemaGenerator(configBuilder.build())
                .generateSchema(Definition.class);
        new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(
                        outputFolder.resolve("schema.json").toFile(),
                        schema);
    }
}
