package com.smotana.dataspray.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CliTest {
    @Test(timeout = 10_000)
    public void test() throws Exception {
        assertEquals(2, Cli.mainWithExitCode(new String[]{}));
    }
}
