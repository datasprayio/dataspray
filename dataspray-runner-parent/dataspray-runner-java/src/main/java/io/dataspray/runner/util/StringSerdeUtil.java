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

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class StringSerdeUtil {

    public final static char DELIMITER = ':';
    public final static char ESCAPER = '\\';

    private StringSerdeUtil() {
        // disable ctor
    }

    public static String mergeStrings(String... ss) {
        if (ss == null || ss.length == 0) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < ss.length; i++) {
            String s = ss[i];
            for (int j = 0; j < s.length(); j++) {
                char c = s.charAt(j);
                switch (c) {
                    case ESCAPER:
                        result.append(ESCAPER).append(ESCAPER);
                        break;
                    case DELIMITER:
                        result.append(ESCAPER).append(DELIMITER);
                        break;
                    default:
                        result.append(c);
                        break;
                }
            }
            if (i + 1 < ss.length) {
                result.append(DELIMITER);
            }
        }
        return result.toString();
    }

    public static String[] unMergeString(String s) {
        if (s == null) {
            return new String[0];
        }
        List<String> results = Lists.newArrayList();
        StringBuilder result = new StringBuilder();
        boolean nextCharEscaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (nextCharEscaped) {
                nextCharEscaped = false;
                result.append(c);
                continue;
            }
            switch (c) {
                case ESCAPER:
                    nextCharEscaped = true;
                    break;
                case DELIMITER:
                    results.add(result.toString());
                    result = new StringBuilder();
                    break;
                default:
                    result.append(c);
                    break;
            }
        }
        results.add(result.toString());
        return results.toArray(new String[0]);
    }
}
