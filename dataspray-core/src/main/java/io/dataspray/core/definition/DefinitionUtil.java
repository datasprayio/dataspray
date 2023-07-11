/*
 * Copyright 2023 Matus Faro
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

