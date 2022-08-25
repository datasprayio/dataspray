package io.dataspray.core.definition.parser;

import com.google.gson.JsonElement;
import io.dataspray.core.definition.model.Definition;

import java.io.InputStream;
import java.io.Reader;

public interface DefinitionLoader {
    Definition fromYaml(Reader definitionReader);

    Definition fromYaml(InputStream definitionInputStream);

    Definition fromYaml(String definitionStr);

    Definition fromJson(String definitionStr);

    Definition fromJson(JsonElement definition);

    String toJson(Definition definition, boolean prettyPrint);

    String toYaml(Definition definition);
}
