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

package io.dataspray.common.test;

import lombok.SneakyThrows;

import java.lang.reflect.Field;

public class TestResourceUtil {

    private TestResourceUtil() {
        // Disallow ctor
    }

    /**
     * Using a TestProfile to add a TestResource in order to register a QuarkusTestResourceLifecycleManager is great,
     * but the test has no way to inject and use the TestResource. A workaround is for the TestResource to inject itself
     * into the test using the inject method. This is a utility to perform the injection.
     * <a href="https://github.com/quarkusio/quarkus/issues/4219">See here for more details.</a>
     */
    @SneakyThrows
    public static void injectSelf(Object testInstance, Object testResource) {
        Class<?> c = testInstance.getClass();
        while (c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (testResource.getClass().isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    f.set(testInstance, testResource);
                    return;
                }
            }
            c = c.getSuperclass();
        }
    }
}
