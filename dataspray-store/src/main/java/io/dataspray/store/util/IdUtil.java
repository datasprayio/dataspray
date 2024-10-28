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

package io.dataspray.store.util;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import java.util.UUID;

@Slf4j
@ApplicationScoped
public class IdUtil {
    private static final int CONTENT_UNIQUE_CONTENT_MAX_LENGTH = 50;
    public static final int CONTENT_UNIQUE_MAX_LENGTH = CONTENT_UNIQUE_CONTENT_MAX_LENGTH + 4;
    public static final int UUID_DASHLESS_MAX_LENGTH = 32;

    public String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String contentUnique(String content) {
        String contentPart = StringUtils.left(content, CONTENT_UNIQUE_CONTENT_MAX_LENGTH)
                .toLowerCase()
                .replaceAll("[^0-9a-z ]+", "")
                .replaceAll(" +", "-")
                .trim();
        int randomChars;
        if (contentPart.length() < 5) {
            randomChars = 8;
        } else if (contentPart.length() < 10) {
            randomChars = 5;
        } else if (contentPart.length() < 15) {
            randomChars = 4;
        } else {
            randomChars = 3;
        }
        return (contentPart + '-' + RandomStringUtils.randomAlphanumeric(randomChars))
                .toLowerCase();
    }

    public String randomId(int charCount) {
        StringBuilder idFullSize = new StringBuilder(randomId());
        while (charCount > idFullSize.length()) {
            idFullSize.append(randomId());
        }
        return idFullSize.substring(0, charCount);
    }

    public UUID parseDashlessUuid(String uuidStr) {
        // From https://stackoverflow.com/questions/18986712/creating-a-uuid-from-a-string-with-no-dashes
        return UUID.fromString(uuidStr.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
    }
}
