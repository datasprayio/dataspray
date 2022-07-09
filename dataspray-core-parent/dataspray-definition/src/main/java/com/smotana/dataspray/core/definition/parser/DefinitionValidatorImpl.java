package com.smotana.dataspray.core.definition.parser;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.smotana.dataspray.core.definition.model.DataFormat;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import com.smotana.dataspray.core.definition.model.KafkaStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
public class DefinitionValidatorImpl implements DefinitionValidator {

    @Override
    public void validate(DataSprayDefinition definition) throws DefinitionLoadingException {
        ImmutableSet<String> dataFormatNames = validateUniqueAndGetResourceNames(
                "data format",
                Optional.ofNullable(definition.getDataFormats()).stream().flatMap(Collection::stream)
                        .map(DataFormat::getName));
        ImmutableSet<String> kafkaStoreNames = validateUniqueAndGetResourceNames(
                "Kafka store",
                Optional.ofNullable(definition.getKafkaStores()).stream().flatMap(Collection::stream)
                        .map(KafkaStore::getName));

        // TODO assert all references are valid, here is an example:
        // Java processors
        if (definition.getJavaProcessors() != null) {
            definition.getJavaProcessors().forEach(processor -> {
                if (processor.getInputs() != null) {
                    processor.getInputs().forEach(dataFormatName -> {
                        if (!dataFormatNames.contains(dataFormatName)) {
                            throw new DefinitionLoadingException("Processor " + processor.getName() + " using input with data format name " + dataFormatName + " not found");
                        }
                    });
                }
                if (processor.getOutputs() != null) {
                    processor.getOutputs().forEach(dataFormatName -> {
                        if (!dataFormatNames.contains(dataFormatName)) {
                            throw new DefinitionLoadingException("Processor " + processor.getName() + " using output with data format name " + dataFormatName + " not found");
                        }
                    });
                }
            });
        }
    }

    private ImmutableSet<String> validateUniqueAndGetResourceNames(String resourceType, Stream<String> resourceNamesStream) throws DefinitionLoadingException {
        HashSet<String> names = Sets.newHashSet();
        resourceNamesStream.forEach(resourceName -> {
            if (!names.add(resourceName)) {
                throw new DefinitionLoadingException("Duplicate " + resourceType + " resources with same name " + resourceName);
            }
        });
        return ImmutableSet.copyOf(names);
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(DefinitionValidator.class).to(DefinitionValidatorImpl.class).asEagerSingleton();
            }
        };
    }
}

