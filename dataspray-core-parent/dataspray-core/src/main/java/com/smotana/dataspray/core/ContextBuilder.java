package com.smotana.dataspray.core;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.smotana.dataspray.core.definition.model.DataFormat;
import com.smotana.dataspray.core.definition.model.Processor;

import java.io.File;
import java.util.Optional;

@Singleton
public class ContextBuilder {

    @Inject
    private ContextUtil contextUtil;

    public DatasprayContext createForFilename(
            DatasprayContext parentContext,
            Project project) {
        return new DatasprayContext(ImmutableMap.<String, Object>builder()
                .putAll(parentContext.getData())
                .put("javaPackagePath", Optional.ofNullable(Strings.emptyToNull(project.getDefinition().getNamespace()))
                        .map(namespace -> namespace.replaceAll("\\.", File.separator) + File.separator)
                        .orElse(""))
                .build());
    }

    public DatasprayContext createForTemplates(
            Project project) {
        return new DatasprayContext(createProjectBase(project)
                .build());
    }

    public DatasprayContext createForDataFormat(
            Project project,
            DataFormat dataFormat) {
        return new DatasprayContext(createProjectBase(project)
                .put("dataFormat", dataFormat)
                .build());
    }

    public DatasprayContext createForProcessor(
            Project project,
            Processor processor) {
        return new DatasprayContext(createProjectBase(project)
                .put("processor", processor)
                .build());
    }

    private ImmutableMap.Builder<String, Object> createProjectBase(
            Project project) {
        return ImmutableMap.<String, Object>builder()
                .put("project", project)
                .put("util", contextUtil);
    }
}
