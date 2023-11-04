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

import com.google.common.collect.ImmutableSet;
import io.dataspray.common.json.GsonUtil;
import io.dataspray.common.test.aws.AbstractLocalstackLifecycleManager;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import io.dataspray.store.DynamoApiGatewayApiAccessStore;
import io.dataspray.store.SingleTableProvider;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@QuarkusIntegrationTest
class AuthorizerIT extends AuthorizerBase {
    ApiAccess apiKey;

    /** Injected via {@link AbstractLocalstackLifecycleManager#inject(Object)} */
    LocalStackContainer localStackContainer;

    DynamoDbClient dynamo;
    SingleTable singleTable;

    @BeforeEach
    public void beforeEach() {
        this.dynamo = createDynamoClient();
        this.singleTable = createSingleTable();
        this.singleTable.createTableIfNotExists(dynamo, SingleTableProvider.LSI_COUNT, SingleTableProvider.GSI_COUNT);
    }

    /**
     * Since an integration test cannot inject resources even for test setup, this method re-implements
     * {@link io.dataspray.store.DynamoApiGatewayApiAccessStore#createApiAccess} to add an API key entry in Dynamo.
     */
    @Override
    protected ApiAccessStore.ApiAccess createApiAccess(
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
                expiryOpt.map(Instant::getEpochSecond).orElse(null));
        TableSchema<ApiAccess> apiKeySchema = singleTable.parseTableSchema(ApiAccess.class);
        dynamo.putItem(PutItemRequest.builder()
                .tableName(apiKeySchema.tableName())
                .item(apiKeySchema.toAttrMap(apiAccess)).build());
        return apiAccess;
    }

    private SingleTable createSingleTable() {
        return SingleTable.builder()
                .tablePrefix(SingleTableProvider.TABLE_PREFIX_DEFAULT)
                .overrideGson(GsonUtil.get())
                .build();
    }

    private DynamoDbClient createDynamoClient() {
        return DynamoDbClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        localStackContainer.getAccessKey(),
                        localStackContainer.getSecretKey())))
                .endpointOverride(localStackContainer.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of(localStackContainer.getRegion()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
