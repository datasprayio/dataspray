package io.dataspray.core.cli;

import com.google.common.collect.ImmutableSet;
import io.dataspray.core.Project;
import io.dataspray.core.definition.model.Item;

import javax.annotation.Nullable;
import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class CommandUtil {

    ImmutableSet<String> getSelectedTaskIds(Project project, @Nullable String parameterTaskId) {
        Optional<String> activeProcessor = Optional.ofNullable(parameterTaskId).or(project::getActiveProcessor);
        if (activeProcessor.isEmpty()) {
            return project.getDefinition()
                    .getProcessors()
                    .stream()
                    .map(Item::getName)
                    .collect(ImmutableSet.toImmutableSet());
        } else {
            return ImmutableSet.of(activeProcessor.get());
        }
    }
}
