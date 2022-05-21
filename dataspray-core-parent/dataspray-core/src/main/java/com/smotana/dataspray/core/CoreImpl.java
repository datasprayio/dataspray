package com.smotana.dataspray.core;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class CoreImpl implements Core {

    @Override
    public String install(String name) {
        return "Installed " + name + "!";
    }
}