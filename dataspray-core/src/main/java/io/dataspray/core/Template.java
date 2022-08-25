package io.dataspray.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
}
