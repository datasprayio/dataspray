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

package io.dataspray.store;

import com.google.gson.annotations.SerializedName;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NonNull;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Store for Athena query execution and management.
 */
public interface QueryStore {

    /**
     * Submit a SQL query for execution.
     *
     * @param organizationName Organization name (for database scoping)
     * @param sqlQuery SQL SELECT query to execute
     * @param username Username submitting the query
     * @return Query execution ID
     */
    String submitQuery(String organizationName, String sqlQuery, String username);

    /**
     * Get query execution status and metadata.
     *
     * @param organizationName Organization name
     * @param queryExecutionId Query execution ID
     * @return Query execution details
     */
    QueryExecution getQueryExecution(String organizationName, String queryExecutionId);

    /**
     * Get query results with pagination.
     *
     * @param organizationName Organization name
     * @param queryExecutionId Query execution ID
     * @param nextToken Pagination token (optional)
     * @param maxResults Maximum number of rows to return
     * @return Query results page
     */
    QueryResultPage getQueryResults(String organizationName, String queryExecutionId,
                                    Optional<String> nextToken, int maxResults);

    /**
     * Get query history for an organization.
     *
     * @param organizationName Organization name
     * @param maxResults Maximum number of queries to return
     * @return List of query executions
     */
    List<QueryExecution> getQueryHistory(String organizationName, int maxResults);

    /**
     * Get database schema (tables and columns).
     *
     * @param organizationName Organization name
     * @return Database schema
     */
    DatabaseSchema getDatabaseSchema(String organizationName);

    /**
     * Query execution details.
     */
    @Value
    @RegisterForReflection
    class QueryExecution {
        @NonNull
        @SerializedName("queryExecutionId")
        String queryExecutionId;

        @NonNull
        @SerializedName("sqlQuery")
        String sqlQuery;

        @NonNull
        @SerializedName("state")
        QueryState state;

        @NonNull
        @SerializedName("submittedAt")
        Instant submittedAt;

        @SerializedName("completedAt")
        Instant completedAt;

        @SerializedName("bytesScanned")
        Long bytesScanned;

        @SerializedName("executionTimeMs")
        Long executionTimeMs;

        @SerializedName("errorMessage")
        String errorMessage;

        @SerializedName("username")
        String username;
    }

    /**
     * Query execution state.
     */
    enum QueryState {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    /**
     * Paginated query results.
     */
    @Value
    @RegisterForReflection
    class QueryResultPage {
        @NonNull
        @SerializedName("columns")
        List<Column> columns;

        @NonNull
        @SerializedName("rows")
        List<List<String>> rows;

        @SerializedName("nextToken")
        String nextToken;
    }

    /**
     * Column metadata.
     */
    @Value
    @RegisterForReflection
    class Column {
        @NonNull
        @SerializedName("name")
        String name;

        @NonNull
        @SerializedName("type")
        String type;
    }

    /**
     * Database schema with tables and columns.
     */
    @Value
    @RegisterForReflection
    class DatabaseSchema {
        @NonNull
        @SerializedName("databaseName")
        String databaseName;

        @NonNull
        @SerializedName("tables")
        List<Table> tables;
    }

    /**
     * Table metadata.
     */
    @Value
    @RegisterForReflection
    class Table {
        @NonNull
        @SerializedName("name")
        String name;

        @NonNull
        @SerializedName("columns")
        List<Column> columns;
    }
}
