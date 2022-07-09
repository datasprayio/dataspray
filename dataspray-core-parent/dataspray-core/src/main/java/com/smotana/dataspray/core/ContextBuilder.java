package com.smotana.dataspray.core;

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.smotana.dataspray.core.definition.model.DataFormat;
import com.smotana.dataspray.core.definition.model.Processor;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
        ImmutableSet<String> inputDataFormatNames = Optional.ofNullable(processor.getInputs()).stream().flatMap(Collection::stream).collect(ImmutableSet.toImmutableSet());
        ImmutableSet<String> outputDataFormatNames = Optional.ofNullable(processor.getOutputs()).stream().flatMap(Collection::stream).collect(ImmutableSet.toImmutableSet());
        Function<String, DataFormat> nameToDataFormatMapper = dataFormatName -> project.getDefinition().getDataFormats().stream()
                .filter(dataFormat -> dataFormat.getName().equals(dataFormatName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find dataFormat with name: " + dataFormatName));
        ImmutableSet<DataFormat> dataFormats = Stream.concat(inputDataFormatNames.stream(), outputDataFormatNames.stream())
                .distinct()
                .map(nameToDataFormatMapper)
                .collect(ImmutableSet.toImmutableSet());
        return new DatasprayContext(createProjectBase(project)
                .put("processor", processor)
                .putAll(filterDataFormats(project, processor))
                .build());
    }

    private ImmutableMap.Builder<String, Object> createProjectBase(
            Project project) {
        return ImmutableMap.<String, Object>builder()
                .put("project", project)
                .put("util", contextUtil);
    }

    private ImmutableMap<String, ImmutableSet<DataFormat>> filterDataFormats(Project project, Processor processor) {
        if (project.getDefinition().getDataFormats() == null || project.getDefinition().getDataFormats().isEmpty()) {
            return ImmutableMap.of();
        }
        Map<String, Set<DataFormat>> formatsByType = Maps.newHashMap();
        Optional.ofNullable(processor.getInputs()).stream().flatMap(Collection::stream).forEach(dataFormatName -> {
            DataFormat dataFormat = project.getDefinition().getDataFormats().stream().filter(df -> df.getName().equals(dataFormatName)).findAny().orElseThrow(() -> new RuntimeException("Data format not found with name " + dataFormatName));
            formatsByType.computeIfAbsent("dataFormats", k -> Sets.newHashSet()).add(dataFormat);
            formatsByType.computeIfAbsent("inputDataFormats", k -> Sets.newHashSet()).add(dataFormat);
            String serdeAsCamelCase = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, dataFormat.getSerde().name());
            formatsByType.computeIfAbsent(serdeAsCamelCase + "InputDataFormats", k -> Sets.newHashSet()).add(dataFormat);
            formatsByType.computeIfAbsent(serdeAsCamelCase + "DataFormats", k -> Sets.newHashSet()).add(dataFormat);
        });
        Optional.ofNullable(processor.getOutputs()).stream().flatMap(Collection::stream).forEach(dataFormatName -> {
            DataFormat dataFormat = project.getDefinition().getDataFormats().stream().filter(df -> df.getName().equals(dataFormatName)).findAny().orElseThrow(() -> new RuntimeException("Data format not found with name " + dataFormatName));
            formatsByType.computeIfAbsent("dataFormats", k -> Sets.newHashSet()).add(dataFormat);
            formatsByType.computeIfAbsent("outputDataFormats", k -> Sets.newHashSet()).add(dataFormat);
            String serdeAsCamelCase = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, dataFormat.getSerde().name());
            formatsByType.computeIfAbsent(serdeAsCamelCase + "OutputDataFormats", k -> Sets.newHashSet()).add(dataFormat);
            formatsByType.computeIfAbsent(serdeAsCamelCase + "DataFormats", k -> Sets.newHashSet()).add(dataFormat);
        });
        return formatsByType.entrySet().stream().collect(ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                e -> ImmutableSet.copyOf(e.getValue())));
    }
}
