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

package io.dataspray.core;

import com.jcabi.aspects.Cacheable;
import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.model.Processor;
import lombok.NonNull;
import lombok.Value;
import org.eclipse.jgit.api.Git;

import java.nio.file.Path;
import java.util.Optional;

@Value
public class Project {
    @NonNull
    Path path;
    @NonNull
    Git git;
    @NonNull
    Definition definition;
    /** If set, cwd points to this processor */
    @NonNull
    Optional<String> activeProcessor;

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public Processor getProcessorByName(String name) {
        return getDefinition().getProcessors().stream()
                .filter(p -> p.getName().equals(name))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Processor not found: " + name));
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public Path getProcessorDir(Processor processor) {
        return getPath().resolve(processor.getNameDir());
    }
}
