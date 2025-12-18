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
import io.dataspray.stream.control.client.model.QueryResultColumn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility for formatting query results in different output formats.
 */
public class TableFormatter {

    /**
     * Print results as an ASCII table.
     */
    public static void printTable(List<QueryResultColumn> columns, List<List<String>> rows) {
        if (columns.isEmpty()) {
            System.out.println("No columns");
            return;
        }

        // Calculate column widths
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).getName().length();
        }

        for (List<String> row : rows) {
            for (int i = 0; i < Math.min(row.size(), widths.length); i++) {
                String value = row.get(i) != null ? row.get(i) : "NULL";
                widths[i] = Math.max(widths[i], value.length());
            }
        }

        // Print header
        StringBuilder headerLine = new StringBuilder("|");
        StringBuilder separatorLine = new StringBuilder("+");

        for (int i = 0; i < columns.size(); i++) {
            String header = padRight(columns.get(i).getName(), widths[i]);
            headerLine.append(" ").append(header).append(" |");
            separatorLine.append("-".repeat(widths[i] + 2)).append("+");
        }

        System.out.println(separatorLine);
        System.out.println(headerLine);
        System.out.println(separatorLine);

        // Print rows
        for (List<String> row : rows) {
            StringBuilder rowLine = new StringBuilder("|");
            for (int i = 0; i < columns.size(); i++) {
                String value = "NULL";
                if (i < row.size() && row.get(i) != null) {
                    value = row.get(i);
                }
                rowLine.append(" ").append(padRight(value, widths[i])).append(" |");
            }
            System.out.println(rowLine);
        }

        System.out.println(separatorLine);
        System.out.println(rows.size() + " row(s)");
    }

    /**
     * Print results as CSV.
     */
    public static void printCsv(List<QueryResultColumn> columns, List<List<String>> rows) {
        // Print header
        System.out.println(columns.stream()
                .map(QueryResultColumn::getName)
                .map(TableFormatter::escapeCsv)
                .collect(Collectors.joining(",")));

        // Print rows
        for (List<String> row : rows) {
            List<String> escapedRow = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                String value = "";
                if (i < row.size() && row.get(i) != null) {
                    value = row.get(i);
                }
                escapedRow.add(escapeCsv(value));
            }
            System.out.println(String.join(",", escapedRow));
        }
    }

    /**
     * Print results as JSON array of objects.
     */
    public static void printJson(List<QueryResultColumn> columns, List<List<String>> rows, Gson gson) {
        List<Map<String, String>> jsonRows = new ArrayList<>();

        for (List<String> row : rows) {
            Map<String, String> jsonRow = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                String value = null;
                if (i < row.size()) {
                    value = row.get(i);
                }
                jsonRow.put(columns.get(i).getName(), value);
            }
            jsonRows.add(jsonRow);
        }

        System.out.println(gson.toJson(jsonRows));
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) {
            return s;
        }
        return s + " ".repeat(n - s.length());
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
