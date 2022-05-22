package com.smotana.dataspray.definition.generator;

import org.jsonschema2pojo.Jsonschema2Pojo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class DefinitionGenerator {
    private final String schemaInputFileYaml;
    private final String schemaOutputFileJson;
    private final String codeGenOutputDir;

    private DefinitionGenerator(
            String schemaInputFileYaml,
            String schemaOutputFileJson,
            String codeGenOutputDir) {
        this.schemaInputFileYaml = schemaInputFileYaml;
        this.schemaOutputFileJson = schemaOutputFileJson;
        this.codeGenOutputDir = codeGenOutputDir;
    }

    public void run() throws IOException {
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
        new YamlToJsonSchema(schemaInputFileYamlUrl, schemaOutputFile).convert();

        // Generate pojos
        System.err.println("SchemaProcessor generating pojo to: " + codeGenOutputDir);
        Jsonschema2Pojo.generate(
                new SchemaGenerationConfig(
                        schemaInputFileYamlUrl,
                        new File(codeGenOutputDir)),
                new SystemRuleLogger());
    }

    public static void main(String[] args) throws Exception {
        assert args.length == 3;
        new DefinitionGenerator(args[0], args[1], args[2]).run();
    }
}
