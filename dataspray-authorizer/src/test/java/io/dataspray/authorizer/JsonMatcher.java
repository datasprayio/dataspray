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

package io.dataspray.authorizer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.dataspray.common.json.GsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

@Slf4j
public class JsonMatcher extends TypeSafeMatcher<Object> {

    private static final Gson gson = GsonUtil.get();
    private final String expectedStr;

    private JsonMatcher(String expectedStr) {
        this.expectedStr = expectedStr;
    }

    @Override
    protected boolean matchesSafely(Object actual) {
        // Prepare actual object by re-parsing it
        String actualStr;
        if (actual instanceof String) {
            actualStr = (String) actual;
        } else {
            actualStr = objToStr(actual);
        }
        String actualStrPretty = objToStr(strToObj(actualStr));

        boolean matches = expectedStr.equals(actualStrPretty);
        log.error("JSON Does not match\n\texpected:\n{}\n\tactual:\n ",
                expectedStr, actualStrPretty);

        return matches;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("JSON equal to ")
                .appendValue(expectedStr);
    }

    private static String objToStr(Object obj) {
        return gson.toJson(obj);
    }

    private static JsonElement strToObj(String str) {
        return JsonParser.parseString(str);
    }

    /**
     * Creates a matcher that matches given object to incoming JSON.
     *
     * <pre>assertThat("{\"a\": \"b\"}", jsonObjectEqualTo(Map.of("a", "b")))</pre>
     */
    public static JsonMatcher jsonObjectEqualTo(Object expectedObj) {
        return new JsonMatcher(objToStr(expectedObj));
    }

    /**
     * Creates a matcher that matches given object to incoming JSON. It works by pretty prenting both using Gson.
     * <pre>assertThat("{\"a\": \"b\"}", jsonStringEqualTo("{\"a\": \"b\"}"))</pre>
     */
    public static JsonMatcher jsonStringEqualTo(String expectedStr) {
        return new JsonMatcher(objToStr(strToObj(expectedStr)));
    }
}
