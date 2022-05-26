package com.smotana.dataspray.core.definition.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

@Slf4j
public class DefinitionLoaderImpl implements DefinitionLoader {

    @Inject
    private DefinitionValidator validator;
    @Inject
    private ObjectMapper objectMapper;

    private ObjectWriter objectWriterPrettyPrint;

    @Inject
    private void setup() {
        this.objectWriterPrettyPrint = objectMapper.copy().writerWithDefaultPrettyPrinter();
    }

    @Override
    public DataSprayDefinition fromYaml(Reader definitionYamlReader) {
        DataSprayDefinition definition;
        try {
            definition = new YAMLMapper().readValue(definitionYamlReader, DataSprayDefinition.class);
        } catch (IOException ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
        validator.validate(definition);
        return definition;
    }

    @Override
    public DataSprayDefinition fromYaml(InputStream definitionYamlInputStream) {
        DataSprayDefinition definition;
        try {
            definition = new YAMLMapper().readValue(definitionYamlInputStream, DataSprayDefinition.class);
        } catch (IOException ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
        validator.validate(definition);
        return definition;
    }

    @Override
    public DataSprayDefinition fromYaml(String definitionYamlStr) {
        DataSprayDefinition definition;
        try {
            definition = new YAMLMapper().readValue(definitionYamlStr, DataSprayDefinition.class);
        } catch (IOException ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
        validator.validate(definition);
        return definition;
    }

    @Override
    public DataSprayDefinition fromJson(String definitionStr) {
        DataSprayDefinition definition;
        try {
            definition = objectMapper.readValue(definitionStr, DataSprayDefinition.class);
        } catch (IOException ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
        validator.validate(definition);
        return definition;
    }

    @Override
    public String toJson(DataSprayDefinition definition, boolean prettyPrint) {
        try {
            return prettyPrint
                    ? objectWriterPrettyPrint.writeValueAsString(definition)
                    : objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
    }

    @Override
    public String toYaml(DataSprayDefinition definition) {
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
