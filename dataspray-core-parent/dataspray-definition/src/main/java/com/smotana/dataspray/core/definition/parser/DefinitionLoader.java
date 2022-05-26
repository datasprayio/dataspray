package com.smotana.dataspray.core.definition.parser;

import com.smotana.dataspray.core.definition.model.DataSprayDefinition;

import java.io.InputStream;
import java.io.Reader;

public interface DefinitionLoader {
    DataSprayDefinition fromYaml(Reader definitionReader);

    DataSprayDefinition fromYaml(InputStream definitionInputStream);

    DataSprayDefinition fromYaml(String definitionStr);

    DataSprayDefinition fromJson(String definitionStr);

    String toJson(DataSprayDefinition definition, boolean prettyPrint);

    String toYaml(DataSprayDefinition definition);
}
