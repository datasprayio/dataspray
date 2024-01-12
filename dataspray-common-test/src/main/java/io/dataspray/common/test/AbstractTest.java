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

package io.dataspray.common.test;

import lombok.SneakyThrows;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractTest {

    /**
     * Fetch a test resource given by name. Expected to be in "{testResources}/{package}/{testClass}/{fileName}"
     * <p>
     * If calling from a test class io.dataspray.web.ExampleLambdaTest for a resource data.json,
     * the expected file should be under test resources in:
     * io/dataspray/web/ExampleLambdaTest/data.json
     *
     * @return String
     */
    protected String getTestResource(String fileName) {
        return new String(getTestResourceBytes(fileName, this.getClass()));
    }

    @SneakyThrows
    protected byte[] getTestResourceBytes(String fileName, Class testClazz) {
        String filePath = testClazz.getCanonicalName().replace(".", File.separator)
                          + File.separator + fileName;
        try (var resource = testClazz
                .getClassLoader()
                .getResourceAsStream(filePath)) {
            return checkNotNull(resource, "Test resource at %s not found", filePath).readAllBytes();
        }
    }
}
