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

package io.dataspray.core.definition.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import org.yaml.snakeyaml.Yaml;

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
