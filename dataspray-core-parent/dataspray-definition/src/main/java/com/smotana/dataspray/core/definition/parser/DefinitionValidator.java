package com.smotana.dataspray.core.definition.parser;

import com.smotana.dataspray.core.definition.model.DataSprayDefinition;

public interface DefinitionValidator {
    void validate(DataSprayDefinition definition) throws DefinitionLoadingException;
}

