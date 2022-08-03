// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package com.smotana.dataspray.core.common.json;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Force Gson to abide by @javax.annotation.Nonnull annotation.
 */
public class JavaxNonnullAdapterFactory implements TypeAdapterFactory {
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        boolean parentHasNonnull = type.getRawType().isAnnotationPresent(Nonnull.class);
        ImmutableList<Field> nonnullFields = Arrays.stream(type.getRawType().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Nonnull.class))
                .collect(ImmutableList.toImmutableList());
        if (!parentHasNonnull && nonnullFields.isEmpty()) {
            return null;
        }
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
        nonnullFields.forEach(field -> field.setAccessible(true));
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                assertNonnull(value);
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                T instance = delegate.read(in);
                assertNonnull(instance);
                return instance;
            }

            private void assertNonnull(T instance) {
                if (instance == null) {
                    if (parentHasNonnull) {
                        throw new JsonSyntaxException("Json missing class " + type.getRawType().getSimpleName());
                    } else {
                        return;
                    }
                }
                for (Field field : nonnullFields) {
                    Object o;
                    try {
                        o = field.get(instance);
                    } catch (IllegalAccessException ex) {
                        throw new IllegalStateException(ex);
                    }
                    if (o == null) {
                        throw new JsonSyntaxException("Json missing non null field " + field.getName() + " in class " + instance.getClass().getSimpleName());
                    }
                }
            }
        };
    }
}
