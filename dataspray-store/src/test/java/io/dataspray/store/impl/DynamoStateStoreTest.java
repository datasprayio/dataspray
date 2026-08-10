/*
 * Copyright 2026 Matus Faro
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

import com.google.common.collect.ImmutableMap;
import io.dataspray.common.test.AbstractTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.CustomerDynamoStore;
import io.dataspray.store.StateStore;
import io.dataspray.store.StateStore.StateEntry;
import io.dataspray.store.util.IdUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class DynamoStateStoreTest extends AbstractTest {

    @Inject
    StateStore stateStore;
    @Inject
    CustomerDynamoStore customerDynamoStore;
    @Inject
    IdUtil idUtil;
    @Inject
    software.amazon.awssdk.services.dynamodb.DynamoDbClient dynamo;

    private String createOrgWithTable() {
        String orgName = idUtil.randomId();
        // Create the table directly; the store-level createTableIfNotExists also touches Cognito
        customerDynamoStore.getSingleTable(orgName).createTableIfNotExists(dynamo, 0, 1);
        return orgName;
    }

    @Test
    public void testUpsertGetRoundTrip() {
        String orgName = createOrgWithTable();
        String[] keyParts = {"task", "myTask", "counter"};

        StateEntry created = stateStore.upsertState(orgName, keyParts, ImmutableMap.of(
                "aString", "hello",
                "aNumber", 42L,
                "aBool", true,
                "aList", List.of("x", "y"),
                "aMap", Map.of("nested", "value")
        ), Optional.of(3600L));

        assertEquals("hello", created.getAttributes().get("aString"));
        assertEquals(new BigDecimal(42), created.getAttributes().get("aNumber"));
        assertEquals(true, created.getAttributes().get("aBool"));
        assertEquals(List.of("x", "y"), created.getAttributes().get("aList"));
        assertEquals(Map.of("nested", "value"), created.getAttributes().get("aMap"));
        assertTrue(created.getTtlInEpochSec().isPresent());

        StateEntry fetched = stateStore.getState(orgName, keyParts).orElseThrow();
        assertEquals(created.getAttributes(), fetched.getAttributes());
    }

    @Test
    public void testPartialUpsertPreservesOtherAttributes() {
        String orgName = createOrgWithTable();
        String[] keyParts = {"task", "myTask", "state"};

        stateStore.upsertState(orgName, keyParts, ImmutableMap.of(
                "keep", "original",
                "change", "before"
        ), Optional.empty());

        // Regression: this used to be a full PutItem which deleted "keep"
        StateEntry updated = stateStore.upsertState(orgName, keyParts, ImmutableMap.of(
                "change", "after"
        ), Optional.empty());

        assertEquals("original", updated.getAttributes().get("keep"));
        assertEquals("after", updated.getAttributes().get("change"));
    }

    @Test
    public void testReservedAttributeNamesRejected() {
        String orgName = createOrgWithTable();
        String[] keyParts = {"task", "myTask", "state"};

        // Regression: attributes named pk/sk/ttlInEpochSec used to overwrite the item key or TTL
        for (String reserved : List.of("pk", "sk", "ttlInEpochSec")) {
            assertThrows(IllegalArgumentException.class, () ->
                    stateStore.upsertState(orgName, keyParts, ImmutableMap.of(reserved, "boom"), Optional.empty()));
        }
    }

    @Test
    public void testInvalidKeyPartsRejected() {
        String orgName = createOrgWithTable();
        assertThrows(IllegalArgumentException.class, () ->
                stateStore.upsertState(orgName, new String[0], ImmutableMap.of("a", "b"), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                stateStore.getState(orgName, new String[]{""}));
    }

    @Test
    public void testListStateWithPrefixAndDelete() {
        String orgName = createOrgWithTable();
        stateStore.upsertState(orgName, new String[]{"task", "a", "one"}, ImmutableMap.of("v", 1L), Optional.empty());
        stateStore.upsertState(orgName, new String[]{"task", "a", "two"}, ImmutableMap.of("v", 2L), Optional.empty());
        stateStore.upsertState(orgName, new String[]{"task", "b", "three"}, ImmutableMap.of("v", 3L), Optional.empty());

        List<StateEntry> all = stateStore.listState(orgName, Optional.empty(), Optional.empty(), 100).getData();
        assertEquals(3, all.size());

        List<StateEntry> prefixed = stateStore.listState(orgName,
                Optional.of(new String[]{"task", "a"}), Optional.empty(), 100).getData();
        assertEquals(2, prefixed.size());

        stateStore.deleteState(orgName, new String[]{"task", "a", "one"});
        assertTrue(stateStore.getState(orgName, new String[]{"task", "a", "one"}).isEmpty());
        assertEquals(2, stateStore.listState(orgName, Optional.empty(), Optional.empty(), 100).getData().size());
    }

    @Test
    public void testMalformedCursorRejected() {
        String orgName = createOrgWithTable();
        assertThrows(IllegalArgumentException.class, () ->
                stateStore.listState(orgName, Optional.empty(), Optional.of("not-base64!!!"), 10));
    }

    @Test
    public void testMissingTable() {
        String orgName = idUtil.randomId();
        // Reads treat a missing table as empty
        assertTrue(stateStore.getState(orgName, new String[]{"task"}).isEmpty());
        assertTrue(stateStore.listState(orgName, Optional.empty(), Optional.empty(), 10).getData().isEmpty());
        // Writes surface a typed error
        assertThrows(StateStore.StateTableNotFoundException.class, () ->
                stateStore.upsertState(orgName, new String[]{"task"}, ImmutableMap.of("a", "b"), Optional.empty()));
    }
}
