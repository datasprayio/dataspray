package com.smotana.dataspray.core.definition.parser;

import com.smotana.dataspray.core.definition.model.DataSprayDefinition;

import java.io.InputStream;
import java.io.Reader;

public interface DefinitionLoader {
    DataSprayDefinition loadAsYaml(Reader definitionReader);

    DataSprayDefinition loadAsYaml(InputStream definitionInputStream);

    DataSprayDefinition loadAsYaml(String definitionStr);

    DataSprayDefinition loadAsJson(String definitionStr);
}
