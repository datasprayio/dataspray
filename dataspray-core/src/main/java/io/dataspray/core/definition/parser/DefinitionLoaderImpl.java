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
