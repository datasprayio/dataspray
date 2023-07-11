/*
 * Copyright 2023 Matus Faro
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

package io.dataspray.core.definition.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.dataspray.common.json.GsonUtil;
import io.dataspray.core.definition.model.Definition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;

@Slf4j
@ApplicationScoped
public class DefinitionLoaderImpl implements DefinitionLoader {

    @Inject
    Yaml yaml;
    @Inject
    SnakeYamlUtil snakeYamlUtil;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    Gson gson;
    @Inject
    @Named(GsonUtil.PRETTY_PRINT)
    Gson gsonPrettyPrint;

    @Override
    public Definition fromYaml(Reader definitionYamlReader) {
        return fromJson(snakeYamlUtil.toGsonElement(yaml.load(definitionYamlReader)));
    }

    @Override
    public Definition fromYaml(InputStream definitionYamlInputStream) {
        return fromJson(snakeYamlUtil.toGsonElement(yaml.load(definitionYamlInputStream)));
    }

    @Override
    public Definition fromYaml(String definitionYamlStr) {
        return fromJson(snakeYamlUtil.toGsonElement(yaml.load(definitionYamlStr)));
    }

    @Override
    public Definition fromJson(String definitionStr) {
        return gson.fromJson(definitionStr, Definition.class);
    }

    @Override
    public Definition fromJson(JsonElement definition) {
        return gson.fromJson(definition, Definition.class);
    }

    @Override
    public String toJson(Definition definition, boolean prettyPrint) {
        return prettyPrint
                ? gsonPrettyPrint.toJson(definition)
                : gson.toJson(definition);
    }

    @Override
    public String toYaml(Definition definition) {
        String definitionJson = toJson(definition, false);
        try {
            return new YAMLMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objectMapper.readTree(definitionJson));
        } catch (JsonProcessingException ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
    }
}
