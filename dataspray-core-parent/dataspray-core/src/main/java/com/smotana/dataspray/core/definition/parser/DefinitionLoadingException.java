package com.smotana.dataspray.core.definition.parser;

public class DefinitionLoadingException extends RuntimeException {

    public DefinitionLoadingException(String message) {
        super(message);
    }

    public DefinitionLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
