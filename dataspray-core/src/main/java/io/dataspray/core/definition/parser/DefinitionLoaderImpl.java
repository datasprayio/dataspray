package io.dataspray.core.definition.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import io.dataspray.core.definition.model.Definition;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;

@Slf4j
public class DefinitionLoaderImpl implements DefinitionLoader {

    @Inject
    private Yaml yaml;
    @Inject
    private ObjectMapper objectMapper;
    @Inject
    private Gson gson;

    private Gson gsonPrettyPrint;

    @Inject
    private void setup() {
        gsonPrettyPrint = gson.newBuilder()
                .setPrettyPrinting()
                .create();
    }

    @Override
    public Definition fromYaml(Reader definitionYamlReader) {
        return fromJson(SnakeYamlUtil.toGsonElement(yaml.load(definitionYamlReader)));
    }

    @Override
    public Definition fromYaml(InputStream definitionYamlInputStream) {
        return fromJson(SnakeYamlUtil.toGsonElement(yaml.load(definitionYamlInputStream)));
    }

    @Override
    public Definition fromYaml(String definitionYamlStr) {
        return fromJson(SnakeYamlUtil.toGsonElement(yaml.load(definitionYamlStr)));
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

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(DefinitionLoader.class).to(DefinitionLoaderImpl.class).asEagerSingleton();
            }
        };
    }
}
