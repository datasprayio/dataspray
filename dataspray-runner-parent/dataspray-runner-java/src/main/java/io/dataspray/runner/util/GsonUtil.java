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

package io.dataspray.runner.util;

import com.dampcake.gson.immutable.ImmutableAdapterFactory;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class GsonUtil {

    private static volatile Gson gson;

    public static Gson get() {
        if (gson == null) {
            synchronized (GsonUtil.class) {
                if (gson == null) {
                    gson = new GsonBuilder()
                            .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                            .disableHtmlEscaping()
                            .registerTypeAdapterFactory(ImmutableAdapterFactory.forGuava())
                            .registerTypeAdapter(Instant.class, new InstantTypeConverter())
                            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeConverter())
                            .registerTypeAdapter(LocalDate.class, new LocalDateTypeConverter())
                            .registerTypeAdapter(LocalTime.class, new LocalTimeTypeConverter())
                            .registerTypeAdapter(Optional.class, new JavaOptionalTypeConverter<>())
                            .create();
                }
            }
        }
        return gson;
    }

    private static class InstantTypeConverter
            implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            String jsonStr = json.getAsString();
            try {
                // Try to parse as ISO-8601 formatted string
                return Instant.parse(jsonStr);
            } catch (DateTimeParseException ex) {
                // If parsing fails, assume it's a millisecond timestamp
                try {
                    long epochMilli = Long.parseLong(jsonStr);
                    return Instant.ofEpochMilli(epochMilli);
                } catch (NumberFormatException ex2) {
                    throw new IllegalArgumentException("Input string '" + jsonStr + "' is neither a valid ISO-8601 date nor a millisecond timestamp.", ex);
                }
            }
        }
    }

    private static class LocalDateTimeTypeConverter
            implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        @Override
        public JsonElement serialize(LocalDateTime src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            return LocalDateTime.parse(json.getAsString());
        }
    }

    private static class LocalDateTypeConverter
            implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        @Override
        public JsonElement serialize(LocalDate src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            return LocalDate.parse(json.getAsString());
        }
    }

    private static class LocalTimeTypeConverter
            implements JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {
        @Override
        public JsonElement serialize(LocalTime src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public LocalTime deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            return LocalTime.parse(json.getAsString());
        }
    }

    private static class JavaOptionalTypeConverter<T>
            implements JsonSerializer<Optional<T>>, JsonDeserializer<Optional<T>> {

        @Override
        public Optional<T> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            Type actualType = ((ParameterizedType) typeOfT).getActualTypeArguments()[0];
            T value = context.deserialize(json, actualType);
            return Optional.ofNullable(value);
        }

        @Override
        public JsonElement serialize(Optional<T> src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.orElse(null));
        }
    }
}
