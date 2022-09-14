package io.dataspray.core;

import com.google.common.collect.ImmutableMap;
import io.dataspray.core.definition.model.DataFormat;
import io.dataspray.core.definition.model.Processor;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ContextBuilder {

    @Inject
    ContextUtil contextUtil;

    public DatasprayContext createForFilename(
            DatasprayContext parentContext,
            Project project) {
        return new DatasprayContext(ImmutableMap.<String, Object>builder()
                .putAll(parentContext.getData())
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
                .put("definition", project.getDefinition())
                .put("util", contextUtil);
    }
}
