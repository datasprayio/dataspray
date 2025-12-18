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

import io.dataspray.common.test.AbstractTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.singletable.SingleTable;
import io.dataspray.store.QueryStore;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.AlreadyExistsException;
import software.amazon.awssdk.services.glue.model.Column;
import software.amazon.awssdk.services.glue.model.CreateDatabaseRequest;
import software.amazon.awssdk.services.glue.model.CreateTableRequest;
import software.amazon.awssdk.services.glue.model.DatabaseInput;
import software.amazon.awssdk.services.glue.model.SerDeInfo;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.TableInput;

import java.util.List;
import java.util.UUID;

import io.dataspray.store.QueryNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class AthenaQueryStoreTest extends AbstractTest {

    @Inject
    QueryStore queryStore;

    @Inject
    AthenaClient athenaClient;

    @Inject
    GlueClient glueClient;

    @Inject
    DynamoDbClient dynamoClient;

    @Inject
    SingleTable singleTable;

    @Inject
    AthenaQueryStore athenaQueryStore;

    private String testOrgName;
    private String testDatabaseName;

    @BeforeEach
    public void setup() {
        testOrgName = "test-org-" + UUID.randomUUID().toString().substring(0, 8);
        testDatabaseName = FirehoseS3AthenaBatchStore.getDatabaseName(getDeployEnv(), testOrgName);

        // Initialize the store
        athenaQueryStore.init();
    }

    @Test
    public void testGetQueryExecution_notFound() {
        String nonExistentQueryId = "non-existent-query-id";

        QueryNotFoundException exception = assertThrows(QueryNotFoundException.class, () -> {
            queryStore.getQueryExecution(testOrgName, nonExistentQueryId);
        });

        assertTrue(exception.getMessage().contains("Query not found"));
    }

    @Test
    public void testGetQueryHistory_empty() {
        // Get history when no queries have been submitted
        List<QueryStore.QueryExecution> history = queryStore.getQueryHistory(testOrgName, 10);

        assertNotNull(history);
        assertEquals(0, history.size());
    }

    @Test
    public void testGetDatabaseSchema_notFound() {
        QueryNotFoundException exception = assertThrows(QueryNotFoundException.class, () -> {
            queryStore.getDatabaseSchema(testOrgName);
        });

        assertTrue(exception.getMessage().contains("No database found"));
    }

    @Test
    public void testGetDatabaseSchema_success() {
        // Create database
        createTestDatabase(testDatabaseName);

        // Create a test table
        createTestTable(testDatabaseName, "stream_events", List.of(
                Column.builder().name("timestamp").type("bigint").build(),
                Column.builder().name("event_type").type("string").build(),
                Column.builder().name("user_id").type("string").build()
        ));

        // Get schema
        QueryStore.DatabaseSchema schema = queryStore.getDatabaseSchema(testOrgName);

        assertNotNull(schema);
        assertEquals(testDatabaseName, schema.getDatabaseName());
        assertEquals(1, schema.getTables().size());

        QueryStore.Table table = schema.getTables().get(0);
        assertEquals("stream_events", table.getName());
        assertEquals(3, table.getColumns().size());

        // Verify columns
        assertTrue(table.getColumns().stream().anyMatch(c -> c.getName().equals("timestamp") && c.getType().equals("bigint")));
        assertTrue(table.getColumns().stream().anyMatch(c -> c.getName().equals("event_type") && c.getType().equals("string")));
        assertTrue(table.getColumns().stream().anyMatch(c -> c.getName().equals("user_id") && c.getType().equals("string")));
    }

    // Helper methods

    private void createTestDatabase(String databaseName) {
        try {
            glueClient.createDatabase(CreateDatabaseRequest.builder()
                    .databaseInput(DatabaseInput.builder()
                            .name(databaseName)
                            .description("Test database for " + testOrgName)
                            .build())
                    .build());
        } catch (AlreadyExistsException e) {
            // Database already exists, ignore
        }
    }

    private void createTestTable(String databaseName, String tableName, List<Column> columns) {
        try {
            glueClient.createTable(CreateTableRequest.builder()
                    .databaseName(databaseName)
                    .tableInput(TableInput.builder()
                            .name(tableName)
                            .storageDescriptor(StorageDescriptor.builder()
                                    .columns(columns)
                                    .location("s3://test-bucket/data/")
                                    .inputFormat("org.apache.hadoop.mapred.TextInputFormat")
                                    .outputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat")
                                    .serdeInfo(SerDeInfo.builder()
                                            .serializationLibrary("org.openx.data.jsonserde.JsonSerDe")
                                            .build())
                                    .build())
                            .build())
                    .build());
        } catch (AlreadyExistsException e) {
            // Table already exists, ignore
        }
    }
}
