/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.cli;

import com.google.common.collect.ImmutableSet;
import io.dataspray.core.Project;
import io.dataspray.core.definition.model.Item;
import jakarta.enterprise.context.ApplicationScoped;

import javax.annotation.Nullable;
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
