package com.smotana.dataspray.core;

import com.smotana.dataspray.core.sample.SampleProject;

public interface Core {

    /**
     * Initialize a new project
     */
    void init(String name, SampleProject sample);

    void install();

    /**
     * Check status of every resource
     */
    void status();

    void deploy();

    void deploy(String processorName);
}