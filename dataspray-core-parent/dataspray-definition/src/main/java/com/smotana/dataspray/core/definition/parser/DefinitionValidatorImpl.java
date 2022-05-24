package com.smotana.dataspray.core.definition.parser;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import com.smotana.dataspray.core.definition.model.KafkaStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

@Slf4j
public class DefinitionValidatorImpl implements DefinitionValidator {

    @Override
    public void validate(DataSprayDefinition definition) throws DefinitionLoadingException {
        ImmutableSet<String> kafkaStoreNames = validateUniqueAndGetResourceNames("KafkaStores", definition.getKafkaStores().stream().flatMap(Collection::stream)
                .map(KafkaStore::getName));

        // TODO assert all references are valid, here is an example:
        // Java processors
        definition.getSamzaProcessors().stream().flatMap(Collection::stream).forEach(processor -> {
            processor.getKafkaStoreNames().stream().flatMap(Collection::stream).forEach(kafkaStoreName -> {
                if (!kafkaStoreNames.contains(kafkaStoreName)) {
                    throw new DefinitionLoadingException("Processor " + processor.getName() + " using non-existent store " + kafkaStoreName);
                }
            });
        });
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

