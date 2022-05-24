package com.smotana.dataspray.core.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Locale;

@Command(name = "install",
        description = "compile and install component(s)")
public class Install implements Runnable {

    @Parameters(arity = "1..*", paramLabel = "<languageCode>", description = "language code(s)")
    private String[] languageCodes;

    @Override
    public void run() {
        for (String code : languageCodes) {
            System.out.printf("%s: %s",
                    code.toLowerCase(), new Locale(code).getDisplayLanguage());
        }
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Install.class).asEagerSingleton();
            }
        };
    }
}
