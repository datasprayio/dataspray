package io.dataspray.core.definition.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.yaml.snakeyaml.Yaml;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

@ApplicationScoped
public class SnakeYamlUtil {

    @Dependent
    Yaml getInstance() {
        return new Yaml();
    }

    /**
     * Wraps the object returned by the Snake YAML parser into a GSON JsonElement
     * representation.
     *
     * https://stackoverflow.com/a/48490088
     */
    public JsonElement toGsonElement(Object o) {

        //NULL => JsonNull
        if (o == null)
            return JsonNull.INSTANCE;

        // Collection => JsonArray
        if (o instanceof Collection) {
            JsonArray array = new JsonArray();
            for (Object childObj : (Collection<?>) o)
                array.add(toGsonElement(childObj));
            return array;
        }

        // Array => JsonArray
        if (o.getClass().isArray()) {
            JsonArray array = new JsonArray();

            int length = Array.getLength(array);
            for (int i = 0; i < length; i++)
                array.add(toGsonElement(Array.get(array, i)));

            return array;
        }

        // Map => JsonObject
        if (o instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) o;

            JsonObject jsonObject = new JsonObject();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final String name = String.valueOf(entry.getKey());
                final Object value = entry.getValue();
                jsonObject.add(name, toGsonElement(value));
            }

            return jsonObject;
        }

        // everything else => JsonPrimitive
        if (o instanceof String)
            return new JsonPrimitive((String) o);
        if (o instanceof Number)
            return new JsonPrimitive((Number) o);
        if (o instanceof Character)
            return new JsonPrimitive((Character) o);
        if (o instanceof Boolean)
            return new JsonPrimitive((Boolean) o);

        // otherwise.. string is a good guess
        return new JsonPrimitive(String.valueOf(o));
    }
}
