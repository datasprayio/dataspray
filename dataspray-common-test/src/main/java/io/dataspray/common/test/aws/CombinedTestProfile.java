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

package io.dataspray.common.test.aws;

import com.google.common.collect.ImmutableList;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class CombinedTestProfile implements QuarkusTestProfile {

    private final ImmutableList<QuarkusTestProfile> profiles;

    @SafeVarargs
    public CombinedTestProfile(Class<? extends QuarkusTestProfile>... profileClazzes) {
        profiles = Arrays.stream(profileClazzes)
                .map(clazz -> {
                    try {
                        return clazz.getConstructor().newInstance();
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                             NoSuchMethodException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return profiles.stream()
                .map(QuarkusTestProfile::getConfigOverrides)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return profiles.stream()
                .map(QuarkusTestProfile::getEnabledAlternatives)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public String getConfigProfile() {
        return profiles.stream()
                .map(QuarkusTestProfile::getConfigProfile)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return profiles.stream()
                .map(QuarkusTestProfile::testResources)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public boolean disableGlobalTestResources() {
        return profiles.stream()
                .anyMatch(QuarkusTestProfile::disableGlobalTestResources);
    }

    @Override
    public Set<String> tags() {
        return profiles.stream()
                .map(QuarkusTestProfile::tags)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public String[] commandLineParameters() {
        return profiles.stream()
                .map(QuarkusTestProfile::commandLineParameters)
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }

    @Override
    public boolean runMainMethod() {
        return profiles.stream()
                .anyMatch(QuarkusTestProfile::runMainMethod);
    }

    @Override
    public boolean disableApplicationLifecycleObservers() {
        return profiles.stream()
                .anyMatch(QuarkusTestProfile::disableApplicationLifecycleObservers);
    }

}
