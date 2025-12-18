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

package io.dataspray.stream.control;

import io.dataspray.common.json.GsonUtil;
import io.dataspray.common.test.aws.AbstractLambdaTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import io.dataspray.store.OrganizationStore.OrganizationMetadata;
import io.dataspray.stream.control.client.model.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupExistsException;
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

import static io.dataspray.common.test.aws.MotoLifecycleManager.CREATE_COGNITO_PARAM;
import static io.dataspray.store.impl.CognitoUserStore.USER_POOL_APP_CLIENT_ID_PROP_NAME;
import static io.dataspray.store.impl.CognitoUserStore.USER_POOL_ID_PROP_NAME;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@QuarkusTest
@QuarkusTestResource(
        value = MotoLifecycleManager.class,
        initArgs = @ResourceArg(name = CREATE_COGNITO_PARAM, value = "true"))
@TestMethodOrder(MethodOrderer.MethodName.class)
public class QueryResourceTest extends AbstractLambdaTest {

    @Inject
    CognitoIdentityProviderClient cognitoClient;

    @Inject
    GlueClient glueClient;

    @ConfigProperty(name = USER_POOL_ID_PROP_NAME)
    String userPoolId;

    @ConfigProperty(name = USER_POOL_APP_CLIENT_ID_PROP_NAME)
    String userPoolClientId;

    private String testDatabaseName;

    @BeforeEach
    public void beforeEach() {
        try {
            cognitoClient.createGroup(CreateGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .groupName(getOrganizationName())
                    .description(GsonUtil.get().toJson(new OrganizationMetadata("authorUser", UsageKeyType.ORGANIZATION_TEN_RPS)))
                    .build());
        } catch (GroupExistsException e) {
            // Group already exists, ignore
        }

        testDatabaseName = "dataspray-test-customer-" + getOrganizationName();
    }

    @Test
    public void testSubmitQuery_invalidDdl() throws Exception {
        String ddlQuery = "CREATE TABLE test (id INT)";

        request(Void.class, Given.builder()
                .method(HttpMethod.POST)
                .path("/v1/organization/" + getOrganizationName() + "/query/submit")
                .body(new SubmitQueryRequest().sqlQuery(ddlQuery))
                .build())
                .assertStatusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testSubmitQuery_invalidDml() throws Exception {
        String dmlQuery = "INSERT INTO test VALUES (1)";

        request(Void.class, Given.builder()
                .method(HttpMethod.POST)
                .path("/v1/organization/" + getOrganizationName() + "/query/submit")
                .body(new SubmitQueryRequest().sqlQuery(dmlQuery))
                .build())
                .assertStatusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testGetDatabaseSchema_notFound() throws Exception {
        request(Void.class, Given.builder()
                .method(HttpMethod.GET)
                .path("/v1/organization/" + getOrganizationName() + "/query/schema")
                .build())
                .assertStatusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testGetDatabaseSchema_success() throws Exception {
        // Create database
        createTestDatabase(testDatabaseName);

        // Create test table
        createTestTable(testDatabaseName, "stream_events", List.of(
                Column.builder().name("timestamp").type("bigint").build(),
                Column.builder().name("event_type").type("string").build()
        ));

        // Get schema
        DatabaseSchemaResponse schema = request(DatabaseSchemaResponse.class, Given.builder()
                .method(HttpMethod.GET)
                .path("/v1/organization/" + getOrganizationName() + "/query/schema")
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();

        assertNotNull(schema);
        assertEquals(testDatabaseName, schema.getDatabaseName());
        assertEquals(1, schema.getTables().size());

        DatabaseTable table = schema.getTables().get(0);
        assertEquals("stream_events", table.getName());
        assertEquals(2, table.getColumns().size());
    }

    @Test
    public void testGetQueryHistory_empty() throws Exception {
        QueryHistoryResponse history = request(QueryHistoryResponse.class, Given.builder()
                .method(HttpMethod.GET)
                .path("/v1/organization/" + getOrganizationName() + "/query/history")
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();

        assertNotNull(history);
        assertNotNull(history.getQueries());
        assertEquals(0, history.getQueries().size());
    }

    // Helper methods

    private void createTestDatabase(String databaseName) {
        try {
            glueClient.createDatabase(CreateDatabaseRequest.builder()
                    .databaseInput(DatabaseInput.builder()
                            .name(databaseName)
                            .description("Test database")
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
