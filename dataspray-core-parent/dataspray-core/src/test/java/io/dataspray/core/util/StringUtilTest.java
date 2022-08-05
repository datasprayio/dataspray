package io.dataspray.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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