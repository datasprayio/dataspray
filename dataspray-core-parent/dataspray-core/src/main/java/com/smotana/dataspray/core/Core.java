package com.smotana.dataspray.core;

import com.smotana.dataspray.core.sample.SampleProject;

import java.io.IOException;

public interface Core {

    /**
     * Initialize a new project
     */
    void init(String name, SampleProject sample) throws IOException;

    void install();

    /**
     * Check status of every resources
     */
    void status();

    void deploy();

}