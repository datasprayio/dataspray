package io.dataspray.common;

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
