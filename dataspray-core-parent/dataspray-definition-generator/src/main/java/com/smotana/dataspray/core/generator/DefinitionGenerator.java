package com.smotana.dataspray.core.generator;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import org.jsonschema2pojo.Jsonschema2Pojo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class DefinitionGenerator {

    @Inject
    private YamlToJsonSchema yamlToJsonSchema;

    public void run(
            String schemaInputFileYaml,
            String schemaOutputFileJson,
            String codeGenOutputDir) throws IOException {
        System.err.println("SchemaProcessor loading yaml from: " + schemaInputFileYaml);
        final URL schemaInputFileYamlUrl;
        File schemaInputFile = new File(schemaInputFileYaml);
        assert schemaInputFile.exists();
        assert schemaInputFile.isFile();
        try {
            schemaInputFileYamlUrl = schemaInputFile.toURI().toURL();
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }

        // Convert yaml to json
        File schemaOutputFile = new File(schemaOutputFileJson);
        if (schemaOutputFile.exists()) {
            schemaOutputFile.delete();
        } else {
            File schemaOutputDir = schemaOutputFile.getParentFile();
            if (schemaOutputDir != null && !schemaOutputDir.exists()) {
                schemaOutputDir.mkdirs();
            }
        }
        System.err.println("SchemaProcessor generating json to: " + schemaOutputFileJson);
        yamlToJsonSchema.convert(schemaInputFileYamlUrl, schemaOutputFile);

        // Generate pojos
        System.err.println("SchemaProcessor generating pojo to: " + codeGenOutputDir);
        Jsonschema2Pojo.generate(
                new SchemaGenerationConfig(
                        schemaInputFileYamlUrl,
                        new File(codeGenOutputDir)),
                new SystemRuleLogger());
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(DefinitionGenerator.class).asEagerSingleton();
            }
        };
    }

    public static void main(String[] args) throws Exception {
        assert args.length == 3;
        GeneratorInjector.INSTANCE.get().getInstance(DefinitionGenerator.class)
                .run(args[0], args[1], args[2]);
    }
}
