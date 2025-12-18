/*
 * Copyright 2025 Matus Faro
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

package io.dataspray.cli.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dataspray.stream.control.client.model.QueryResultColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TableFormatterTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    public void testPrintTable_alignsColumns() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint"),
                new QueryResultColumn().name("name").type("string")
        );
        List<List<String>> rows = List.of(
                List.of("1", "Alice"),
                List.of("12345", "Bob")
        );

        TableFormatter.printTable(columns, rows);

        String output = outContent.toString();

        // Verify output contains headers
        assertTrue(output.contains("id"));
        assertTrue(output.contains("name"));

        // Verify output contains data
        assertTrue(output.contains("Alice"));
        assertTrue(output.contains("Bob"));
        assertTrue(output.contains("12345"));

        // Verify output has borders
        assertTrue(output.contains("+"));
        assertTrue(output.contains("|"));
        assertTrue(output.contains("-"));

        // Verify row count
        assertTrue(output.contains("2 row(s)"));
    }

    @Test
    public void testPrintTable_handlesNullValues() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint"),
                new QueryResultColumn().name("value").type("string")
        );
        List<List<String>> rows = List.of(
                Arrays.asList("1", null),
                Arrays.asList("2", "data")
        );

        TableFormatter.printTable(columns, rows);

        String output = outContent.toString();

        // Verify NULL is displayed for null values
        assertTrue(output.contains("NULL"));
        assertTrue(output.contains("data"));
    }

    @Test
    public void testPrintTable_emptyRows() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint"),
                new QueryResultColumn().name("name").type("string")
        );
        List<List<String>> rows = List.of();

        TableFormatter.printTable(columns, rows);

        String output = outContent.toString();

        // Verify headers are still printed
        assertTrue(output.contains("id"));
        assertTrue(output.contains("name"));

        // Verify row count is zero
        assertTrue(output.contains("0 row(s)"));
    }

    @Test
    public void testPrintCsv_basicOutput() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint"),
                new QueryResultColumn().name("name").type("string")
        );
        List<List<String>> rows = List.of(
                List.of("1", "Alice"),
                List.of("2", "Bob")
        );

        TableFormatter.printCsv(columns, rows);

        String output = outContent.toString();
        String[] lines = output.split("\n");

        // Verify header
        assertEquals("id,name", lines[0]);

        // Verify rows
        assertEquals("1,Alice", lines[1]);
        assertEquals("2,Bob", lines[2]);
    }

    @Test
    public void testPrintCsv_escapesSpecialCharacters() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint"),
                new QueryResultColumn().name("data").type("string")
        );
        List<List<String>> rows = List.of(
                List.of("1", "value,with,commas"),
                List.of("2", "value\"with\"quotes"),
                List.of("3", "value\nwith\nnewlines")
        );

        TableFormatter.printCsv(columns, rows);

        String output = outContent.toString();
        String[] lines = output.split("\n", -1); // -1 to preserve trailing empty strings

        // Verify header
        assertEquals("id,data", lines[0]);

        // Verify escaped values
        assertEquals("1,\"value,with,commas\"", lines[1]);
        assertEquals("2,\"value\"\"with\"\"quotes\"", lines[2]);
        assertTrue(lines[3].startsWith("3,\"value"));
    }

    @Test
    public void testPrintCsv_handlesNullAndEmpty() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint"),
                new QueryResultColumn().name("value").type("string")
        );
        List<List<String>> rows = List.of(
                Arrays.asList("1", null),
                Arrays.asList("2", "")
        );

        TableFormatter.printCsv(columns, rows);

        String output = outContent.toString();
        String[] lines = output.split("\n");

        // Verify null is represented as empty string
        assertEquals("1,", lines[1]);
        assertEquals("2,", lines[2]);
    }

    @Test
    public void testPrintJson_basicOutput() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint"),
                new QueryResultColumn().name("name").type("string")
        );
        List<List<String>> rows = List.of(
                List.of("1", "Alice"),
                List.of("2", "Bob")
        );

        TableFormatter.printJson(columns, rows, gson);

        String output = outContent.toString().trim();

        // Verify it's valid JSON
        assertDoesNotThrow(() -> gson.fromJson(output, List.class));

        // Verify structure
        assertTrue(output.startsWith("["));
        assertTrue(output.endsWith("]"));
        assertTrue(output.contains("\"id\""));
        assertTrue(output.contains("\"name\""));
        assertTrue(output.contains("\"Alice\""));
        assertTrue(output.contains("\"Bob\""));
    }

    @Test
    public void testPrintJson_handlesNullValues() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint"),
                new QueryResultColumn().name("value").type("string")
        );
        List<List<String>> rows = List.of(
                Arrays.asList("1", null),
                Arrays.asList("2", "data")
        );

        TableFormatter.printJson(columns, rows, gson);

        String output = outContent.toString().trim();

        // Verify it's valid JSON
        assertDoesNotThrow(() -> gson.fromJson(output, List.class));

        // Verify null is preserved
        assertTrue(output.contains("null"));
        assertTrue(output.contains("\"data\""));
    }

    @Test
    public void testPrintJson_emptyRows() {
        List<QueryResultColumn> columns = List.of(
                new QueryResultColumn().name("id").type("bigint")
        );
        List<List<String>> rows = List.of();

        TableFormatter.printJson(columns, rows, gson);

        String output = outContent.toString().trim();

        // Verify it's an empty JSON array
        assertEquals("[]", output);
    }
}
