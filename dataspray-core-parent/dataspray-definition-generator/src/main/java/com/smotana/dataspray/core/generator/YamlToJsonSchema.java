package com.smotana.dataspray.core.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class YamlToJsonSchema {

    @Inject
    private ObjectMapper objectMapper;

    public void convert(URL schemaInputFileYamlUrl, File schemaOutputFile) throws IOException {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        JsonNode node = yamlReader.readTree(schemaInputFileYamlUrl);

        node = migrateExtends(node);

        ObjectWriter jsonWriter = new ObjectMapper()
                .writerWithDefaultPrettyPrinter();
        jsonWriter.writeValue(schemaOutputFile, node);
    }

    /**
     * Jsonschema2Pojo does not support allOf, but it does support the deprecated extends.
     * We use extends in the Yaml schema and convert back to allOf here to match latest schema
     * definition.
     */
    private JsonNode migrateExtends(JsonNode node) {
        if (node instanceof ObjectNode) {
            JsonNode fieldExtends = node.get("extends");
            List<String> fieldNames = StreamSupport.stream(Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false)
                    .collect(Collectors.toList());
            boolean hasExtends = false;
            for (String fieldName : fieldNames) {
                if ("extends".equals(fieldName)) {
                    hasExtends = true;
                    ((ObjectNode) node).remove(fieldName);
                } else {
                    ((ObjectNode) node).replace(fieldName, migrateExtends(node.get(fieldName)));
                }
            }
            if (hasExtends) {
                ObjectNode allOfNode = ((ObjectNode) node).objectNode();
                allOfNode.set("allOf", ((ObjectNode) node).arrayNode(2)
                        .add(fieldExtends)
                        .add(node));
                return allOfNode;
            } else {
                return node;
            }
        }
        return node;
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(YamlToJsonSchema.class).asEagerSingleton();
            }
        };
    }
}
