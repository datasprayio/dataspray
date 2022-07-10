package com.smotana.dataspray.core.definition.parser;

import com.smotana.dataspray.core.definition.model.Definition;

import java.io.InputStream;
import java.io.Reader;

public interface DefinitionLoader {
    Definition fromYaml(Reader definitionReader);

    Definition fromYaml(InputStream definitionInputStream);

    Definition fromYaml(String definitionStr);

    Definition fromJson(String definitionStr);

    String toJson(Definition definition, boolean prettyPrint);

    String toYaml(Definition definition);
}
