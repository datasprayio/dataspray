package io.dataspray.core.definition;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.dataspray.core.definition.parser.DefinitionLoadingException;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.stream.Stream;

@Slf4j
public class DefinitionUtil {

    public static ImmutableSet<String> validateUniqueAndGetResourceNames(String resourceType, Stream<String> resourceNamesStream) throws DefinitionLoadingException {
        HashSet<String> names = Sets.newHashSet();
        resourceNamesStream.forEach(resourceName -> {
            if (!names.add(resourceName)) {
                throw new DefinitionLoadingException("Duplicate " + resourceType + " resources with same name " + resourceName);
            }
        });
        return ImmutableSet.copyOf(names);
    }

}

