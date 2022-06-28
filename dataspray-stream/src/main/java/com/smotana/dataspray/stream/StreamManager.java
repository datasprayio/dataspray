package com.smotana.dataspray.stream;

public interface StreamManager {

    void setupEnvironment();

    /**
     * Take a jar and deploy it onto a Lambda.
     */
    void deploy();
}
