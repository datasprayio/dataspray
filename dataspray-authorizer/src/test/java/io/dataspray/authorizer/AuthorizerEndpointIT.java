/*
 * Copyright 2023 Matus Faro
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

package io.dataspray.authorizer;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.singletable.TableType;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import io.dataspray.store.DynamoApiGatewayApiAccessStore;
import io.dataspray.store.SingleTableProvider;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.localstack.LocalStackContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.LongStream;

import static io.dataspray.singletable.TableType.*;

@Slf4j
@QuarkusIntegrationTest
@QuarkusTestResource(AuthorizerLocalstackLifecycleManager.class)
class AuthorizerEndpointIT extends AuthorizerEndpointBase {
    ApiAccess apiKey;

    /** Injected via {@link io.dataspray.common.aws.test.AbstractLocalstackLifecycleManager#inject(Object)} */
    LocalStackContainer localStackContainer;

    Optional<SingleTable> singleTableCache = Optional.empty();

    /**
     * Since an integration test cannot inject resources even for test setup, this method re-implements
     * {@link io.dataspray.store.DynamoApiGatewayApiAccessStore#createApiAccess} to add an API key entry in Dynamo.
     */
    @Override
    ApiAccessStore.ApiAccess createApiAccess(
            String accountId,
            ApiAccessStore.UsageKeyType usageKeyType,
            String description,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt) {

        ApiAccess apiAccess = new ApiAccess(
                new KeygenUtil().generateSecureApiKey(DynamoApiGatewayApiAccessStore.API_KEY_LENGTH),
                accountId,
                usageKeyType.getId(),
                description,
                queueWhitelistOpt.orElse(ImmutableSet.of()),
                expiryOpt.map(Instant::toEpochMilli).orElse(null));
        SingleTable singleTable = getSingleTable();
        singleTable.createTableIfNotExists(SingleTableProvider.LSI_COUNT, SingleTableProvider.GSI_COUNT);
        TableSchema<ApiAccess> apiKeySchema = singleTable.parseTableSchema(ApiAccess.class);
        apiKeySchema.table().putItem(apiKeySchema.toItem(apiAccess));
        return apiAccess;
    }

    private SingleTable getSingleTable() {
        if (singleTableCache.isPresent()) {
            return singleTableCache.get();
        }

        AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                        localStackContainer.getAccessKey(),
                        localStackContainer.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        localStackContainer.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString(),
                        localStackContainer.getRegion()))
                .build();
        SingleTable singleTable = SingleTable.builder()
                .overrideDynamo(amazonDynamoDB)
                .tablePrefix(SingleTableProvider.TABLE_PREFIX_DEFAULT)
                .build();

        /*
         * TODO Remove this call once the bug is fixed in SingleTable.
         * LocalStack version <=2.2.0 has a bug that prevents SingleTable to detect DynamoDB table does not exist and fails to
         * create it. The bug is fixed but not yet released. Upgrade to anything above 2.2.0.
         */
        createSingleTableTable(amazonDynamoDB);

        singleTableCache = Optional.of(singleTable);
        return singleTableCache.get();
    }

    private void createSingleTableTable(AmazonDynamoDB amazonDynamoDB) {
        ArrayList<KeySchemaElement> primaryKeySchemas = Lists.newArrayList();
        ArrayList<AttributeDefinition> primaryAttributeDefinitions = Lists.newArrayList();
        ArrayList<LocalSecondaryIndex> localSecondaryIndexes = Lists.newArrayList();
        ArrayList<GlobalSecondaryIndex> globalSecondaryIndexes = Lists.newArrayList();

        primaryKeySchemas.add(new KeySchemaElement(getPartitionKeyName(Primary, -1), KeyType.HASH));
        primaryAttributeDefinitions.add(new AttributeDefinition(getPartitionKeyName(Primary, -1), ScalarAttributeType.S));
        primaryKeySchemas.add(new KeySchemaElement(getRangeKeyName(Primary, -1), KeyType.RANGE));
        primaryAttributeDefinitions.add(new AttributeDefinition(getRangeKeyName(Primary, -1), ScalarAttributeType.S));

        LongStream.range(1, SingleTableProvider.LSI_COUNT + 1).forEach(indexNumber -> {
            localSecondaryIndexes.add(new LocalSecondaryIndex()
                    .withIndexName(getTableOrIndexName(Lsi, indexNumber, SingleTableProvider.TABLE_PREFIX_DEFAULT))
                    .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                    .withKeySchema(ImmutableList.of(
                            new KeySchemaElement(getPartitionKeyName(Lsi, indexNumber), KeyType.HASH),
                            new KeySchemaElement(getRangeKeyName(Lsi, indexNumber), KeyType.RANGE))));
            primaryAttributeDefinitions.add(new AttributeDefinition(getRangeKeyName(Lsi, indexNumber), ScalarAttributeType.S));
        });

        LongStream.range(1, SingleTableProvider.GSI_COUNT + 1).forEach(indexNumber -> {
            globalSecondaryIndexes.add(new GlobalSecondaryIndex()
                    .withIndexName(getTableOrIndexName(Gsi, indexNumber, SingleTableProvider.TABLE_PREFIX_DEFAULT))
                    .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                    .withKeySchema(ImmutableList.of(
                            new KeySchemaElement(getPartitionKeyName(Gsi, indexNumber), KeyType.HASH),
                            new KeySchemaElement(getRangeKeyName(Gsi, indexNumber), KeyType.RANGE))));
            primaryAttributeDefinitions.add(new AttributeDefinition(getPartitionKeyName(Gsi, indexNumber), ScalarAttributeType.S));
            primaryAttributeDefinitions.add(new AttributeDefinition(getRangeKeyName(Gsi, indexNumber), ScalarAttributeType.S));
        });

        String tableName = getTableOrIndexName(Primary, -1, SingleTableProvider.TABLE_PREFIX_DEFAULT);
        CreateTableRequest createTableRequest = new CreateTableRequest()
                .withTableName(tableName)
                .withKeySchema(primaryKeySchemas)
                .withAttributeDefinitions(primaryAttributeDefinitions)
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
        if (!localSecondaryIndexes.isEmpty()) {
            createTableRequest.withLocalSecondaryIndexes(localSecondaryIndexes);
        }
        if (!globalSecondaryIndexes.isEmpty()) {
            createTableRequest.withGlobalSecondaryIndexes(globalSecondaryIndexes);
        }
        new DynamoDB(amazonDynamoDB).createTable(createTableRequest);
        log.info("Table {} created", tableName);
    }

    private String getPartitionKeyName(TableType type, long indexNumber) {
        return type == Primary || type == Lsi
                ? "pk"
                : type.name().toLowerCase() + "pk" + indexNumber;
    }

    private String getRangeKeyName(TableType type, long indexNumber) {
        return type == Primary
                ? "sk"
                : type.name().toLowerCase() + "sk" + indexNumber;
    }

    private String getTableOrIndexName(TableType type, long indexNumber, String tablePrefix) {
        return tablePrefix + (type == Primary
                ? type.name().toLowerCase()
                : type.name().toLowerCase() + indexNumber);
    }
}
