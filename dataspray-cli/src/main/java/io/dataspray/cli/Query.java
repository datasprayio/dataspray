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

package io.dataspray.cli;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.dataspray.cli.util.TableFormatter;
import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.StreamRuntime;
import io.dataspray.stream.control.client.model.*;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Command(name = "query",
        description = "Execute and manage Athena queries",
        subcommands = {
                Query.Execute.class,
                Query.Status.class,
                Query.Results.class,
                Query.History.class,
                Query.Schema.class
        })
public class Query implements Runnable {

    @Override
    public void run() {
        System.err.println("Use subcommands: execute, status, results, history, schema");
        System.err.println("Run 'dst query --help' for more information");
    }

    @Slf4j
    @Command(name = "execute",
            aliases = {"exec"},
            description = "Execute SQL query against data lake")
    static class Execute implements Runnable {
        @Mixin
        LoggingMixin loggingMixin;
        @Option(names = {"-p", "--profile"}, description = "Profile name")
        String profileName;
        @Option(names = {"-q", "--query"}, description = "SQL query to execute")
        String sqlQuery;
        @Option(names = {"-f", "--file"}, description = "File containing SQL query")
        String queryFile;
        @Option(names = {"--wait"}, description = "Wait for query to complete and display results")
        boolean wait;
        @Option(names = {"--output"}, description = "Output format: table, json, csv", defaultValue = "table")
        OutputFormat outputFormat;

        @Inject
        StreamRuntime streamRuntime;
        @Inject
        Codegen codegen;
        @Inject
        CliConfig cliConfig;
        @Inject
        Gson gson;

        @Override
        public void run() {
            Project project = codegen.loadProject();

            // Get SQL query from --query or --file
            String sql = getSqlQuery();

            // Submit query
            SubmitQueryResponse response = streamRuntime.submitQuery(
                    cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName))),
                    project,
                    sql
            );

            String queryId = response.getQueryExecutionId();
            System.out.println("Query submitted: " + queryId);

            if (wait) {
                // Poll for completion
                System.out.println("Waiting for query to complete...");
                QueryExecutionStatus status = pollUntilComplete(queryId);

                if ("SUCCEEDED".equals(status.getState().getValue())) {
                    System.out.println("Query completed successfully");
                    System.out.println("Bytes scanned: " + formatBytes(status.getBytesScanned()));
                    System.out.println("Execution time: " + status.getExecutionTimeMs() + "ms");
                    System.out.println();

                    // Fetch and display results
                    QueryResultsResponse results = streamRuntime.getQueryResults(
                            cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName))),
                            project,
                            queryId,
                            null,
                            100
                    );

                    displayResults(results);
                } else {
                    System.err.println("Query failed: " + status.getErrorMessage());
                    System.exit(1);
                }
            }
        }

        private String getSqlQuery() {
            if (sqlQuery != null && queryFile != null) {
                System.err.println("Error: Cannot specify both --query and --file");
                System.exit(1);
            }

            if (sqlQuery != null) {
                return sqlQuery;
            }

            if (queryFile != null) {
                try {
                    return Files.readString(Paths.get(queryFile));
                } catch (IOException e) {
                    System.err.println("Error reading query file: " + e.getMessage());
                    System.exit(1);
                }
            }

            System.err.println("Error: Must specify either --query or --file");
            System.exit(1);
            return null;
        }

        private QueryExecutionStatus pollUntilComplete(String queryId) {
            while (true) {
                QueryExecutionStatus status = streamRuntime.getQueryStatus(
                        cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName))),
                        codegen.loadProject(),
                        queryId
                );

                String state = status.getState().getValue();
                if ("SUCCEEDED".equals(state) || "FAILED".equals(state) || "CANCELLED".equals(state)) {
                    return status;
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for query", e);
                }
            }
        }

        private void displayResults(QueryResultsResponse results) {
            switch (outputFormat) {
                case table:
                    TableFormatter.printTable(results.getColumns(), results.getRows());
                    break;
                case json:
                    TableFormatter.printJson(results.getColumns(), results.getRows(), gson);
                    break;
                case csv:
                    TableFormatter.printCsv(results.getColumns(), results.getRows());
                    break;
            }
        }

        private String formatBytes(Long bytes) {
            if (bytes == null || bytes == 0) return "0 B";
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int unitIndex = 0;
            double size = bytes;
            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }
            return String.format("%.2f %s", size, units[unitIndex]);
        }
    }

    @Slf4j
    @Command(name = "status", description = "Check query execution status")
    static class Status implements Runnable {
        @Mixin
        LoggingMixin loggingMixin;
        @Option(names = {"-p", "--profile"}, description = "Profile name")
        String profileName;
        @Parameters(index = "0", description = "Query execution ID")
        String queryExecutionId;

        @Inject
        StreamRuntime streamRuntime;
        @Inject
        Codegen codegen;
        @Inject
        CliConfig cliConfig;

        @Override
        public void run() {
            Project project = codegen.loadProject();

            QueryExecutionStatus status = streamRuntime.getQueryStatus(
                    cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName))),
                    project,
                    queryExecutionId
            );

            System.out.println("Query ID: " + status.getQueryExecutionId());
            System.out.println("State: " + status.getState().getValue());
            System.out.println("Submitted: " + formatTimestamp(status.getSubmittedAt()));
            if (status.getCompletedAt() != null) {
                System.out.println("Completed: " + formatTimestamp(status.getCompletedAt()));
            }
            if (status.getBytesScanned() != null) {
                System.out.println("Bytes scanned: " + formatBytes(status.getBytesScanned()));
            }
            if (status.getExecutionTimeMs() != null) {
                System.out.println("Execution time: " + status.getExecutionTimeMs() + "ms");
            }
            if (status.getErrorMessage() != null) {
                System.out.println("Error: " + status.getErrorMessage());
            }
        }

        private String formatTimestamp(Object timestamp) {
            if (timestamp == null) return "N/A";
            return DateTimeFormatter.ISO_INSTANT.format((java.time.Instant) timestamp);
        }

        private String formatBytes(Long bytes) {
            if (bytes == null || bytes == 0) return "0 B";
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int unitIndex = 0;
            double size = bytes;
            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }
            return String.format("%.2f %s", size, units[unitIndex]);
        }
    }

    @Slf4j
    @Command(name = "results", description = "Get query results")
    static class Results implements Runnable {
        @Mixin
        LoggingMixin loggingMixin;
        @Option(names = {"-p", "--profile"}, description = "Profile name")
        String profileName;
        @Parameters(index = "0", description = "Query execution ID")
        String queryExecutionId;
        @Option(names = {"--output"}, description = "Output format: table, json, csv", defaultValue = "table")
        OutputFormat outputFormat;
        @Option(names = {"--max-results"}, description = "Maximum number of rows", defaultValue = "100")
        int maxResults;

        @Inject
        StreamRuntime streamRuntime;
        @Inject
        Codegen codegen;
        @Inject
        CliConfig cliConfig;
        @Inject
        Gson gson;

        @Override
        public void run() {
            Project project = codegen.loadProject();

            QueryResultsResponse results = streamRuntime.getQueryResults(
                    cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName))),
                    project,
                    queryExecutionId,
                    null,
                    maxResults
            );

            switch (outputFormat) {
                case table:
                    TableFormatter.printTable(results.getColumns(), results.getRows());
                    break;
                case json:
                    TableFormatter.printJson(results.getColumns(), results.getRows(), gson);
                    break;
                case csv:
                    TableFormatter.printCsv(results.getColumns(), results.getRows());
                    break;
            }

            if (results.getNextToken() != null) {
                System.err.println("\nMore results available. Use --max-results to fetch more.");
            }
        }
    }

    @Slf4j
    @Command(name = "history", description = "View query history")
    static class History implements Runnable {
        @Mixin
        LoggingMixin loggingMixin;
        @Option(names = {"-p", "--profile"}, description = "Profile name")
        String profileName;
        @Option(names = {"--max-results"}, description = "Maximum number of queries", defaultValue = "50")
        int maxResults;

        @Inject
        StreamRuntime streamRuntime;
        @Inject
        Codegen codegen;
        @Inject
        CliConfig cliConfig;

        @Override
        public void run() {
            Project project = codegen.loadProject();

            QueryHistoryResponse history = streamRuntime.getQueryHistory(
                    cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName))),
                    project,
                    maxResults
            );

            if (history.getQueries().isEmpty()) {
                System.out.println("No query history found");
                return;
            }

            // Print as table
            System.out.printf("%-38s %-12s %-20s %-15s %-60s%n",
                    "Query ID", "State", "Submitted", "Bytes Scanned", "SQL");
            System.out.println("-".repeat(145));

            for (QueryExecutionStatus query : history.getQueries()) {
                System.out.printf("%-38s %-12s %-20s %-15s %-60s%n",
                        query.getQueryExecutionId(),
                        query.getState().getValue(),
                        formatTimestamp(query.getSubmittedAt()),
                        formatBytes(query.getBytesScanned()),
                        truncate(query.getSqlQuery(), 60)
                );
            }
        }

        private String formatTimestamp(Object timestamp) {
            if (timestamp == null) return "N/A";
            return DateTimeFormatter.ISO_INSTANT.format((java.time.Instant) timestamp);
        }

        private String formatBytes(Long bytes) {
            if (bytes == null || bytes == 0) return "0 B";
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int unitIndex = 0;
            double size = bytes;
            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }
            return String.format("%.2f %s", size, units[unitIndex]);
        }

        private String truncate(String str, int maxLen) {
            if (str == null) return "";
            return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
        }
    }

    @Slf4j
    @Command(name = "schema", description = "View available tables and columns")
    static class Schema implements Runnable {
        @Mixin
        LoggingMixin loggingMixin;
        @Option(names = {"-p", "--profile"}, description = "Profile name")
        String profileName;

        @Inject
        StreamRuntime streamRuntime;
        @Inject
        Codegen codegen;
        @Inject
        CliConfig cliConfig;

        @Override
        public void run() {
            Project project = codegen.loadProject();

            DatabaseSchemaResponse schema = streamRuntime.getDatabaseSchema(
                    cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName))),
                    project
            );

            System.out.println("Database: " + schema.getDatabaseName());
            System.out.println();

            for (DatabaseTable table : schema.getTables()) {
                System.out.println("Table: " + table.getName());
                System.out.printf("  %-40s %-20s%n", "Column", "Type");
                System.out.println("  " + "-".repeat(60));
                for (QueryResultColumn column : table.getColumns()) {
                    System.out.printf("  %-40s %-20s%n", column.getName(), column.getType());
                }
                System.out.println();
            }
        }
    }

    enum OutputFormat {
        table, json, csv
    }
}
