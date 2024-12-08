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

package io.dataspray.common.json;

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
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class GsonUtil {
    public static final String PRETTY_PRINT = "pretty-print";
    private static volatile Gson gson;
    private static volatile Gson gsonPrettyPrint;

    @ApplicationScoped
    @DefaultBean
    Gson getInstance() {
        return get();
    }

    @ApplicationScoped
    @Named(PRETTY_PRINT)
    Gson getInstancePrettyPrint() {
        return getPrettyPrint();
    }

    public static Gson get() {
        if (gson == null) {
            synchronized (GsonUtil.class) {
                if (gson == null) {
                    gson = new GsonBuilder()
                            .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                            .disableHtmlEscaping()
                            .registerTypeAdapterFactory(ImmutableAdapterFactory.forGuava())
                            .registerTypeAdapterFactory(new NonnullAdapterFactory())
                            .registerTypeAdapter(Instant.class, new InstantTypeConverter())
                            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeConverter())
                            .registerTypeAdapter(LocalDate.class, new LocalDateTypeConverter())
                            .registerTypeAdapter(LocalTime.class, new LocalTimeTypeConverter())
                            .registerTypeAdapter(Optional.class, new JavaOptionalTypeConverter<>())
                            .registerTypeAdapterFactory(ExplicitNull.get())
                            .create();
                }
            }
        }
        return gson;
    }

    public static Gson getPrettyPrint() {
        if (gsonPrettyPrint == null) {
            synchronized (GsonUtil.class) {
                if (gsonPrettyPrint == null) {
                    gsonPrettyPrint = get()
                            .newBuilder()
                            .setPrettyPrinting()
                            .create();
                }
            }
        }
        return gsonPrettyPrint;
    }

    private static class InstantTypeConverter
            implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            return Instant.parse(json.getAsString());
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
