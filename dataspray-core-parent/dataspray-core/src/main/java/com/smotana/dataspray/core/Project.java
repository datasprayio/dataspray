package com.smotana.dataspray.core;

import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import lombok.Value;

import java.nio.file.Path;

@Value
public class Project {
    Path path;
    DataSprayDefinition definition;
}
