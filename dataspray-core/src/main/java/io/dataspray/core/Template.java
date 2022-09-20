package io.dataspray.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.file.Path;

@Getter
@AllArgsConstructor
public enum Template {
    TEMPLATES("template"),
    SCHEMAS("schema"),

    DATA_FORMAT_JSON("data-format-json"),
    DATA_FORMAT_PROTOBUF("data-format-protobuf"),
    DATA_FORMAT_AVRO("data-format-avro"),

    JAVA("java");

    private final String resourceName;

    public TemplateFiles getFilesFromResources() {
        return new TemplateFiles(this);
    }

    public TemplateFiles getFilesFromDisk(Path templateDir) {
        return new TemplateFiles(this, templateDir);
    }
}
