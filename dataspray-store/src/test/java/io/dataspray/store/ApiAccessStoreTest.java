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

package io.dataspray.store;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.dataspray.common.test.aws.MotoInstance;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import io.dataspray.store.ApiAccessStore.UsageKey;
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import io.dataspray.store.impl.DynamoApiGatewayApiAccessStore;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class ApiAccessStoreTest {

    MotoInstance motoInstance;

    @Inject
    ApiAccessStore apiAccessStore;
    @Inject
    DynamoDbClient dynamo;
    @Inject
    SingleTable singleTable;
    @Inject
    KeygenUtil keygenUtil;
    @Inject
    ApiGatewayClient apiGatewayClient;

    @Test
    public void testSet() throws Exception {
        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();
        ApiAccess apiAccess = apiAccessStore.createApiAccessForUser(
                organizationName,
                username,
                UsageKeyType.UNLIMITED,
                Optional.empty(),
                Optional.empty());

        assertEquals(organizationName, apiAccess.getOrganizationName());
        assertEquals(username, apiAccess.getOwnerUsername());
        assertEquals(UsageKeyType.UNLIMITED, apiAccess.getUsageKeyType());
        assertEquals(Set.of(), apiAccess.getQueueWhitelist());
        assertTrue(apiAccess.isTtlNotExpired());
    }

    @Test
    public void testSetUsageAccountWide() throws Exception {
        String organizationName = UUID.randomUUID().toString();
        String userEmail = UUID.randomUUID().toString() + "@example.com";
        ApiAccess apiAccess1 = apiAccessStore.createApiAccessForUser(
                organizationName,
                userEmail,
                // Should cause a fetch miss for usage key, then create it
                UsageKeyType.ORGANIZATION,
                Optional.of(ImmutableSet.of("queue1", "queue2")),
                Optional.of(Instant.now().plusSeconds(300)));

        assertTrue(usageKeyExists(apiAccess1.getApiKey()));
        assertEquals(Set.of("queue1", "queue2"), apiAccess1.getQueueWhitelist());
        assertTrue(apiAccess1.isTtlNotExpired());

        ApiAccess apiAccess2 = apiAccessStore.createApiAccessForUser(
                organizationName,
                userEmail,
                // Should fetch the previously created usage key
                UsageKeyType.ORGANIZATION,
                Optional.of(ImmutableSet.of("queue1", "queue2")),
                Optional.of(Instant.now().plusSeconds(300)));

        assertTrue(usageKeyExists(apiAccess2.getApiKey()));
    }

    @Test
    public void testCannotCreateExpired() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                apiAccessStore.createApiAccessForUser(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID() + "@example.com",
                        UsageKeyType.UNLIMITED,
                        Optional.empty(),
                        Optional.of(Instant.now().minusSeconds(10))));
    }

    @Test
    public void testGetByApiKey() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UsageKeyType.UNLIMITED,
                Optional.empty(),
                Optional.empty());

        // Cache not used
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), false));
        // Cache miss
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
        // Cache hit
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));

        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey("non-existent", false));
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey("non-existent", true));
    }

    @Test
    public void testGetByApiKeyExpired() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UsageKeyType.UNLIMITED,
                Optional.empty(),
                Optional.of(Instant.now().minusSeconds(10)));

        // Cache not used
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), false));
        // Cache miss
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
        // Cache hit
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
    }

    @Test
    public void testGetByAccount() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UsageKeyType.UNLIMITED,
                Optional.empty(),
                Optional.empty());

        assertEquals(Set.of(apiAccess), apiAccessStore.getApiAccessesByOrganizationName(apiAccess.getOrganizationName()));

        ApiAccess apiAccess2 = apiAccessStore.createApiAccessForUser(
                apiAccess.getOrganizationName(),
                apiAccess.getOwnerUsername(),
                UsageKeyType.UNLIMITED,
                Optional.of(ImmutableSet.of("queue1", "queue2")),
                Optional.of(Instant.now().plusSeconds(300)));

        assertEquals(Set.of(apiAccess, apiAccess2), apiAccessStore.getApiAccessesByOrganizationName(apiAccess.getOrganizationName()));

        assertEquals(Set.of(), apiAccessStore.getApiAccessesByOrganizationName("non-existent"));
    }

    @Test
    public void testGetByAccountExpired() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UsageKeyType.UNLIMITED,
                Optional.empty(),
                Optional.of(Instant.now().minusSeconds(10)));

        assertEquals(Set.of(), apiAccessStore.getApiAccessesByOrganizationName(apiAccess.getOrganizationName()));
    }

    @Test
    public void testRevoke() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UsageKeyType.UNLIMITED,
                Optional.empty(),
                Optional.empty());

        // Cache miss, populates cache
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));

        apiAccessStore.revokeApiKey(apiAccess.getApiKey());

        // Cache hit, should be still present in cache
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
        // Cache not used, realizes key is revoked, invalidates cache
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), false));
        // Cache hit, should be revoked
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
        // Also access by account id should be revoked
        assertEquals(Set.of(), apiAccessStore.getApiAccessesByOrganizationName(apiAccess.getOrganizationName()));
    }

    @Test
    public void testUsageKeyCreateGet() throws Exception {
        String apiKey1 = UUID.randomUUID().toString();
        UsageKey usageKey1 = apiAccessStore.getOrCreateUsageKey(apiKey1);
        assertEquals(apiKey1, usageKey1.getApiKey());

        String apiKey2 = UUID.randomUUID().toString();
        UsageKey usageKey2a = apiAccessStore.getOrCreateUsageKey(apiKey2);
        UsageKey usageKey2b = apiAccessStore.getOrCreateUsageKey(apiKey2);
        assertEquals(apiKey2, usageKey2a.getApiKey());
        assertEquals(apiKey2, usageKey2b.getApiKey());
        assertEquals(usageKey2a.getUsageKeyId(), usageKey2b.getUsageKeyId());
    }

    @Test
    public void testUsageKeyScan() throws Exception {
        UsageKey usageKey1 = apiAccessStore.getOrCreateUsageKey(UUID.randomUUID().toString());
        UsageKey usageKey2 = apiAccessStore.getOrCreateUsageKey(UUID.randomUUID().toString());

        Set<UsageKey> allUsageKeys = Sets.newHashSet();
        apiAccessStore.getAllUsageKeys(allUsageKeys::addAll);

        assertTrue(allUsageKeys.contains(usageKey1));
        assertTrue(allUsageKeys.contains(usageKey2));
    }

    /**
     * Identical to apiAccessStore.createApiAccess but sidelines the code being tested
     * as well as doesn't throw if you try to insert an expired key.
     */
    private ApiAccess createApiAccessInDb(
            UsageKeyType usageKeyType,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt) {

        // Create access object
        ApiAccess apiAccess = new ApiAccess(
                keygenUtil.generateSecureApiKey(DynamoApiGatewayApiAccessStore.API_KEY_LENGTH),
                UUID.randomUUID().toString(),
                ApiAccessStore.OwnerType.USER,
                UUID.randomUUID() + "user@example.com",
                null,
                null,
                usageKeyType,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null));

        // Parse dynamo schema
        TableSchema<ApiAccess> apiAccessSchema = singleTable.parseTableSchema(ApiAccess.class);

        // Insert into dynamo
        dynamo.putItem(PutItemRequest.builder()
                .tableName(apiAccessSchema.tableName())
                .item(apiAccessSchema.toAttrMap(apiAccess))
                .build());

        return apiAccess;
    }

    private boolean usageKeyExists(String apiKey) {
        TableSchema<UsageKey> usageKeySchema = singleTable.parseTableSchema(UsageKey.class);
        Optional<UsageKey> usageKeyOpt = Optional.ofNullable(usageKeySchema.fromAttrMap(dynamo.getItem(GetItemRequest.builder()
                .tableName(usageKeySchema.tableName())
                .key(usageKeySchema.primaryKey(Map.of(
                        "apiKey", apiKey)))
                .build()).item()));
        return usageKeyOpt.isPresent();
    }
}
