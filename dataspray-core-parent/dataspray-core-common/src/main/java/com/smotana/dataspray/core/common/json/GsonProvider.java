package com.smotana.dataspray.core.common.json;

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
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Singleton
public class GsonProvider implements Provider<Gson> {
    private volatile Gson gson;

    @Override
    public Gson get() {
        if (gson == null) {
            synchronized (GsonProvider.class) {
                if (gson == null) {
                    gson = new GsonBuilder()
                            .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                            .disableHtmlEscaping()
                            .registerTypeAdapterFactory(ImmutableAdapterFactory.forGuava())
                            .registerTypeAdapterFactory(new GsonNonNullAdapterFactory())
                            .registerTypeAdapter(Instant.class, new InstantTypeConverter())
                            .registerTypeAdapter(LocalDate.class, new LocalDateTypeConverter())
                            .registerTypeAdapterFactory(ExplicitNull.get())
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
            return Instant.parse(json.getAsString());
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

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Gson.class).toProvider(GsonProvider.class).asEagerSingleton();
            }
        };
    }
}
