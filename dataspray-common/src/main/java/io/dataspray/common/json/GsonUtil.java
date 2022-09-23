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
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;

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
                            .registerTypeAdapterFactory(new JavaxNonnullAdapterFactory())
                            .registerTypeAdapter(Instant.class, new InstantTypeConverter())
                            .registerTypeAdapter(LocalDate.class, new LocalDateTypeConverter())
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
}
