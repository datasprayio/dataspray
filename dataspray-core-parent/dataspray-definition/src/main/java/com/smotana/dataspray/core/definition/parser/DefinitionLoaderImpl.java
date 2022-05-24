package com.smotana.dataspray.core.definition.parser;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;

@Slf4j
public class DefinitionLoaderImpl implements DefinitionLoader {

    @Inject
    private Gson gson;
    @Inject
    private DefinitionValidator validator;

    @Override
    public DataSprayDefinition loadAsYaml(Reader definitionYamlReader) {
        String definitionJsonStr;
        try {
            definitionJsonStr = gson.toJson(new Yaml().load(definitionYamlReader));
        } catch (Exception ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
        return loadAsJson(definitionJsonStr);
    }

    @Override
    public DataSprayDefinition loadAsYaml(InputStream definitionYamlInputStream) {
        String definitionJsonStr;
        try {
            definitionJsonStr = gson.toJson(new Yaml().load(definitionYamlInputStream));
        } catch (Exception ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
        return loadAsJson(definitionJsonStr);
    }

    @Override
    public DataSprayDefinition loadAsYaml(String definitionYamlStr) {
        String definitionJsonStr;
        try {
            definitionJsonStr = gson.toJson(new Yaml().load(definitionYamlStr));
        } catch (Exception ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }
        return loadAsJson(definitionJsonStr);
    }

    @Override
    public DataSprayDefinition loadAsJson(String definitionStr) {
        DataSprayDefinition definition;
        try {
            definition = gson.fromJson(definitionStr, DataSprayDefinition.class);
        } catch (JsonSyntaxException ex) {
            throw new DefinitionLoadingException(ex.getMessage(), ex);
        }

        validator.validate(definition);

        return definition;
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
