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

package io.dataspray.store.impl;

import com.google.common.annotations.VisibleForTesting;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.singletable.IndexSchema;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.QueryNotFoundException;
import io.dataspray.store.QueryStore;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatistics;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.glue.model.GetTablesRequest;
import software.amazon.awssdk.services.glue.model.GetTablesResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.dataspray.common.DeployEnvironment.DEPLOY_ENVIRONMENT_PROP_NAME;
import static io.dataspray.store.impl.FirehoseS3AthenaBatchStore.*;

/**
 * Implementation of QueryStore using AWS Athena for query execution.
 */
@Slf4j
@ApplicationScoped
public class AthenaQueryStore implements QueryStore {

    private static final Duration QUERY_HISTORY_TTL = Duration.ofDays(7);

    // Forbidden SQL keywords (DDL/DML operations)
    private static final List<String> FORBIDDEN_KEYWORDS = Arrays.asList(
            "CREATE", "DROP", "ALTER", "INSERT", "UPDATE", "DELETE",
            "TRUNCATE", "GRANT", "REVOKE", "MERGE"
    );

    // Pattern to extract database names from SQL (basic implementation)
    private static final Pattern DATABASE_PATTERN = Pattern.compile(
            "FROM\\s+(?:`([^`]+)`|([\\w-]+))\\.",
            Pattern.CASE_INSENSITIVE
    );

    @ConfigProperty(name = DEPLOY_ENVIRONMENT_PROP_NAME)
    DeployEnvironment deployEnv;

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;

    @ConfigProperty(name = ETL_BUCKET_PROP_NAME)
    String etlBucketName;

    @Inject
    AthenaClient athenaClient;

    @Inject
    GlueClient glueClient;

    @Inject
    SingleTable singleTable;

    @Inject
    CustomerLogger customerLog;

    @Inject
    DynamoDbClient dynamoClient;

    private TableSchema<QueryHistoryRecord> queryHistorySchema;
    private IndexSchema<QueryHistoryRecord> queryHistoryByQueryExecutionIdSchema;

    @Startup
    @VisibleForTesting
    public void init() {
        queryHistorySchema = singleTable.parseTableSchema(QueryHistoryRecord.class);
        queryHistoryByQueryExecutionIdSchema = singleTable.parseGlobalSecondaryIndexSchema(1, QueryHistoryRecord.class);
    }

    @Override
    public String submitQuery(String organizationName, String sqlQuery, String username) {
        log.info("Submitting query for organization: {}", organizationName);

        // 1. Validate SQL query (defense in depth - IAM is primary security)
        validateSqlQuery(sqlQuery);

        // 2. Get database name
        String databaseName = FirehoseS3AthenaBatchStore.getDatabaseName(deployEnv, organizationName);

        // 3. Verify database exists
        try {
            glueClient.getDatabase(GetDatabaseRequest.builder()
                    .name(databaseName)
                    .build());
        } catch (EntityNotFoundException ex) {
            throw new IllegalArgumentException("No database found for organization: " + organizationName +
                                          ". Please ingest data with batch enabled first.");
        }

        // 4. Build output location
        String outputLocation = buildOutputLocation(organizationName);

        // 5. Start query execution
        StartQueryExecutionRequest request = StartQueryExecutionRequest.builder()
                .queryString(sqlQuery)
                .queryExecutionContext(QueryExecutionContext.builder()
                        .database(databaseName)
                        .build())
                .resultConfiguration(ResultConfiguration.builder()
                        .outputLocation(outputLocation)
                        .build())
                .build();

        StartQueryExecutionResponse response = athenaClient.startQueryExecution(request);
        String queryExecutionId = response.queryExecutionId();

        // 6. Store query metadata in DynamoDB
        storeQueryHistory(organizationName, queryExecutionId, sqlQuery, username);

        log.info("Query submitted successfully: {}", queryExecutionId);
        return queryExecutionId;
    }

    @Override
    public QueryExecution getQueryExecution(String organizationName, String queryExecutionId) {
        log.debug("Getting query execution: {}", queryExecutionId);

        // 1. Verify query belongs to organization
        verifyQueryOwnership(organizationName, queryExecutionId);

        // 2. Get query execution from Athena
        GetQueryExecutionRequest request = GetQueryExecutionRequest.builder()
                .queryExecutionId(queryExecutionId)
                .build();

        GetQueryExecutionResponse response = athenaClient.getQueryExecution(request);
        software.amazon.awssdk.services.athena.model.QueryExecution execution = response.queryExecution();

        // 3. Map to our model
        return mapToQueryExecution(execution);
    }

    @Override
    public QueryResultPage getQueryResults(String organizationName, String queryExecutionId,
                                           Optional<String> nextToken, int maxResults) {
        log.debug("Getting query results: {} (maxResults: {})", queryExecutionId, maxResults);

        // 1. Verify query belongs to organization
        verifyQueryOwnership(organizationName, queryExecutionId);

        // 2. Check query state
        QueryExecution execution = getQueryExecution(organizationName, queryExecutionId);
        if (execution.getState() != QueryState.SUCCEEDED) {
            throw new IllegalArgumentException("Query has not succeeded yet. Current state: " + execution.getState());
        }

        // 3. Get query results from Athena
        GetQueryResultsRequest.Builder requestBuilder = GetQueryResultsRequest.builder()
                .queryExecutionId(queryExecutionId)
                .maxResults(Math.min(maxResults, 1000)); // Cap at 1000

        nextToken.ifPresent(requestBuilder::nextToken);

        GetQueryResultsResponse response = athenaClient.getQueryResults(requestBuilder.build());
        ResultSet resultSet = response.resultSet();

        // 4. Extract column information
        List<Column> columns = resultSet.resultSetMetadata().columnInfo().stream()
                .map(col -> new Column(col.name(), col.type()))
                .collect(Collectors.toList());

        // 5. Extract rows (skip header row if on first page)
        List<List<String>> rows = new ArrayList<>();
        List<Row> athenaRows = resultSet.rows();

        int startIndex = (nextToken.isEmpty() && !athenaRows.isEmpty()) ? 1 : 0; // Skip header on first page
        for (int i = startIndex; i < athenaRows.size(); i++) {
            Row row = athenaRows.get(i);
            List<String> rowData = row.data().stream()
                    .map(Datum::varCharValue)
                    .collect(Collectors.toList());
            rows.add(rowData);
        }

        return new QueryResultPage(columns, rows, response.nextToken());
    }

    @Override
    public List<QueryExecution> getQueryHistory(String organizationName, int maxResults) {
        log.debug("Getting query history for organization: {} (maxResults: {})", organizationName, maxResults);

        // Query DynamoDB for query history
        //  Use custom key condition to query by partition key only
        Map.Entry<String, AttributeValue> pkEntry = queryHistorySchema.partitionKey(Map.of("organizationName", organizationName));
        List<QueryHistoryRecord> records = queryHistorySchema.query()
                .builder(b -> b
                        .keyConditionExpression("#pk = :pk AND begins_with(#sk, :sk)")
                        .expressionAttributeNames(Map.of("#pk", pkEntry.getKey(), "#sk", "sk"))
                        .expressionAttributeValues(Map.of(
                                ":pk", pkEntry.getValue(),
                                ":sk", AttributeValue.fromS("query")))  // rangePrefix from @DynamoTable annotation
                        .scanIndexForward(false)
                        .limit(maxResults))
                .executeStream(dynamoClient)
                .collect(Collectors.toList());

        // Fetch Athena execution details for each query
        return records.stream()
                .map(record -> {
                    try {
                        GetQueryExecutionRequest request = GetQueryExecutionRequest.builder()
                                .queryExecutionId(record.getQueryExecutionId())
                                .build();
                        GetQueryExecutionResponse response = athenaClient.getQueryExecution(request);
                        return mapToQueryExecution(response.queryExecution());
                    } catch (Exception ex) {
                        log.warn("Failed to fetch query execution details for: {}", record.getQueryExecutionId(), ex);
                        // Return basic info from DynamoDB
                        return new QueryExecution(
                                record.getQueryExecutionId(),
                                record.getSqlQuery(),
                                QueryState.FAILED,
                                Instant.ofEpochMilli(record.getSubmissionTime()),
                                null,
                                null,
                                null,
                                "Unable to fetch query details",
                                record.getUsername()
                        );
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public DatabaseSchema getDatabaseSchema(String organizationName) {
        log.debug("Getting database schema for organization: {}", organizationName);

        String databaseName = FirehoseS3AthenaBatchStore.getDatabaseName(deployEnv, organizationName);

        // Verify database exists
        try {
            glueClient.getDatabase(GetDatabaseRequest.builder()
                    .catalogId(awsAccountId)
                    .name(databaseName)
                    .build());
        } catch (EntityNotFoundException ex) {
            throw new QueryNotFoundException("No database found for organization: " + organizationName);
        }

        // Get all tables in the database
        GetTablesRequest request = GetTablesRequest.builder()
                .catalogId(awsAccountId)
                .databaseName(databaseName)
                .build();

        GetTablesResponse response = glueClient.getTables(request);

        List<Table> tables = response.tableList().stream()
                .map(glueTable -> {
                    List<Column> columns = glueTable.storageDescriptor().columns().stream()
                            .map(col -> new Column(col.name(), col.type()))
                            .collect(Collectors.toList());
                    return new Table(glueTable.name(), columns);
                })
                .collect(Collectors.toList());

        return new DatabaseSchema(databaseName, tables);
    }

    /**
     * Build S3 output location for query results.
     */
    private String buildOutputLocation(String organizationName) {
        return "s3://" + etlBucketName + "/" +
               ETL_BUCKET_ATHENA_RESULTS_PREFIX
                       .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ORGANIZATION + "}", organizationName);
    }

    /**
     * Validate SQL query to prevent DDL/DML operations.
     * Defense in depth - IAM permissions are the primary security mechanism.
     */
    private void validateSqlQuery(String sqlQuery) {
        if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }

        // Normalize query for keyword checking (uppercase, remove comments)
        String normalizedQuery = sqlQuery.toUpperCase();

        // Check for forbidden keywords
        for (String keyword : FORBIDDEN_KEYWORDS) {
            // Use word boundary check to avoid false positives (e.g., "INSERTED" shouldn't match "INSERT")
            if (normalizedQuery.matches(".*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException(
                        "Query contains forbidden operation: " + keyword + ". Only SELECT queries are allowed.");
            }
        }
    }

    /**
     * Store query metadata in DynamoDB for history tracking.
     */
    private void storeQueryHistory(String organizationName, String queryExecutionId,
                                   String sqlQuery, String username) {
        long now = System.currentTimeMillis();
        long ttl = Instant.now().plus(QUERY_HISTORY_TTL).getEpochSecond();

        QueryHistoryRecord record = new QueryHistoryRecord(
                organizationName,
                queryExecutionId,
                now,
                sqlQuery,
                username,
                ttl
        );

        queryHistorySchema.put()
                .item(record)
                .execute(dynamoClient);
    }

    /**
     * Verify query belongs to the organization (authorization check).
     */
    private void verifyQueryOwnership(String organizationName, String queryExecutionId) {
        Optional<QueryHistoryRecord> recordOpt = queryHistoryByQueryExecutionIdSchema.query()
                .keyConditionsEqualsPrimaryKey(Map.of("queryExecutionId", queryExecutionId))
                .executeStream(dynamoClient)
                .findFirst();

        if (recordOpt.isEmpty()) {
            throw new QueryNotFoundException("Query not found: " + queryExecutionId);
        }

        if (!recordOpt.get().getOrganizationName().equals(organizationName)) {
            throw new QueryNotFoundException("Query not found: " + queryExecutionId);
        }
    }

    /**
     * Map Athena QueryExecution to our model.
     */
    private QueryExecution mapToQueryExecution(
            software.amazon.awssdk.services.athena.model.QueryExecution athenaExecution) {

        QueryExecutionStatus status = athenaExecution.status();
        QueryExecutionStatistics stats = athenaExecution.statistics();

        // Get username from DynamoDB record
        String username = queryHistoryByQueryExecutionIdSchema.query()
                .keyConditionsEqualsPrimaryKey(Map.of("queryExecutionId", athenaExecution.queryExecutionId()))
                .executeStream(dynamoClient)
                .findFirst()
                .map(QueryHistoryRecord::getUsername)
                .orElse(null);

        return new QueryExecution(
                athenaExecution.queryExecutionId(),
                athenaExecution.query(),
                mapQueryState(status.state()),
                status.submissionDateTime(),
                status.completionDateTime(),
                stats != null ? stats.dataScannedInBytes() : null,
                stats != null ? stats.engineExecutionTimeInMillis() : null,
                status.athenaError() != null ? status.athenaError().errorMessage() : null,
                username
        );
    }

    /**
     * Map Athena QueryExecutionState to our QueryState.
     */
    private QueryState mapQueryState(QueryExecutionState athenaState) {
        return switch (athenaState) {
            case QUEUED -> QueryState.QUEUED;
            case RUNNING -> QueryState.RUNNING;
            case SUCCEEDED -> QueryState.SUCCEEDED;
            case FAILED -> QueryState.FAILED;
            case CANCELLED -> QueryState.CANCELLED;
            default -> QueryState.FAILED;
        };
    }
}
