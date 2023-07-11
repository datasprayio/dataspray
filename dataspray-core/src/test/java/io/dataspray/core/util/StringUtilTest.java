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

package io.dataspray.core.util;

import io.dataspray.common.StringUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class StringUtilTest {

    @Test
    void camelCase() {
        assertEquals("AbCd", StringUtil.camelCase("AbCd", true));
        assertEquals("abCd", StringUtil.camelCase("AbCd", false));
        assertEquals("AbCd", StringUtil.camelCase("abCd", true));
        assertEquals("abCd", StringUtil.camelCase("abCd", false));
        assertEquals("AbCd", StringUtil.camelCase(" ab cd ", true));
        assertEquals("abCd", StringUtil.camelCase(" ab cd ", false));
        assertEquals("AbCd", StringUtil.camelCase(" AB CD ", true));
        assertEquals("abCd", StringUtil.camelCase(" AB CD ", false));
    }
}