package io.dataspray.core;

import io.dataspray.core.sample.SampleProject;

import java.io.FileNotFoundException;

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

    void deploy(String processorName) throws FileNotFoundException;
}