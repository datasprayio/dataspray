package com.smotana.dataspray.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Template {
    TEMPLATES("templates"),
    JAVA("java");
    String resourceName;
}
