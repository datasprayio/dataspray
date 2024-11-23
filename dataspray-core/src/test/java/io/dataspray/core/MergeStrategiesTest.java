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

import io.dataspray.common.test.AbstractTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@QuarkusTest
class MergeStrategiesTest extends AbstractTest {
    @Inject
    MergeStrategies mergeStrategies;

    private Path workingDir;

    @BeforeEach
    @SneakyThrows
    public void beforeEach() {
        workingDir = Files.createTempDirectory(MergeStrategiesTest.class.getSimpleName());
        workingDir.toFile().deleteOnExit();
    }

    public enum TestType {
        GITIGNORE,
        JSON,
        NONE
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TestType.class)
    void test(TestType type) throws Exception {
        String fileExtension = "." + type.name().toLowerCase();
        Optional<MergeStrategies.MergeStrategy> mergeStrategyOpt = mergeStrategies.findMergeStrategy("abc" + fileExtension + CodegenImpl.MUSTACHE_FILE_EXTENSION_MERGE);
        if (TestType.NONE == type) {
            assertEquals(Optional.empty(), mergeStrategyOpt);
            return;
        }
        assertTrue(mergeStrategyOpt.isPresent());

        // First write out the original
        Path targetFile = workingDir.resolve("target" + fileExtension);
        Files.write(targetFile, getTestResourceBytes("original" + fileExtension));

        // Apply the merge
        mergeStrategyOpt.get().merge(
                // With the merge file
                getTestResource("merge" + fileExtension),
                // Against the original
                targetFile);

        // Assert the original was augmented to expected
        assertEquals(
                getTestResource("expected" + fileExtension),
                Files.readString(targetFile));
    }
}