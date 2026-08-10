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

package io.dataspray.cli;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
public class CliTest {

    @Test
    @Launch(value = {}, exitCode = 2)
    public void testNoArgsPrintsUsageWithSubcommands(LaunchResult result) throws Exception {
        assertEquals(2, result.exitCode());

        // Missing subcommand should print usage listing the available subcommands
        String output = result.getOutput() + System.lineSeparator() + result.getErrorOutput();
        assertTrue(output.contains("Usage:"), "Expected usage text in output, got:\n" + output);
        assertTrue(output.contains("dst"), "Expected command name 'dst' in usage, got:\n" + output);
        for (String subcommand : new String[]{"init", "deploy", "query", "status", "upload-schema"}) {
            assertTrue(output.contains(subcommand),
                    "Expected usage to list subcommand '" + subcommand + "', got:\n" + output);
        }
    }
}
