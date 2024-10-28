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
import io.dataspray.common.test.AbstractTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.LambdaStore.LambdaRecord;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class LambdaStoreTest extends AbstractTest {

    @Inject
    LambdaStore lambdaStore;

    @Test
    public void testSetGet() throws Exception {
        String organizationName1 = UUID.randomUUID().toString();
        String organizationName2 = UUID.randomUUID().toString();

        LambdaRecord record1 = setTask(organizationName1, "task1");

        assertEquals(Optional.empty(), lambdaStore.get("otherOrg", record1.getTaskId()));
        assertEquals(Optional.empty(), lambdaStore.get(record1.getOrganizationName(), "otherTask"));
        assertEquals(Optional.of(record1), lambdaStore.get(record1.getOrganizationName(), record1.getTaskId()));

        assertEquals(ImmutableSet.<LambdaRecord>of(), ImmutableSet.copyOf(lambdaStore.getForOrganization("otherOrg", true, Optional.empty()).getItems()));
        assertEquals(ImmutableSet.of(record1), ImmutableSet.copyOf(lambdaStore.getForOrganization(record1.getOrganizationName(), true, Optional.empty()).getItems()));

        record1 = setTask(organizationName1, record1.getTaskId());
        LambdaRecord record2 = setTask(organizationName1, "task2");
        LambdaRecord record3 = setTask(organizationName2, "task3");

        assertEquals(Optional.of(record1), lambdaStore.get(record1.getOrganizationName(), record1.getTaskId()));
        assertEquals(Optional.of(record2), lambdaStore.get(record2.getOrganizationName(), record2.getTaskId()));
        assertEquals(Optional.of(record3), lambdaStore.get(record3.getOrganizationName(), record3.getTaskId()));

        assertEquals(ImmutableSet.of(record1, record2), ImmutableSet.copyOf(lambdaStore.getForOrganization(record1.getOrganizationName(), true, Optional.empty()).getItems()));
        assertEquals(ImmutableSet.of(record3), ImmutableSet.copyOf(lambdaStore.getForOrganization(record3.getOrganizationName(), true, Optional.empty()).getItems()));

        record1 = lambdaStore.markDeleted(record1.getOrganizationName(), record1.getTaskId());
        assertEquals(ImmutableSet.of(record1, record2), ImmutableSet.copyOf(lambdaStore.getForOrganization(record1.getOrganizationName(), true, Optional.empty()).getItems()));
        assertEquals(ImmutableSet.of(record2), ImmutableSet.copyOf(lambdaStore.getForOrganization(record1.getOrganizationName(), false, Optional.empty()).getItems()));
    }

    @Test
    public void testMarkDeleted() throws Exception {
        LambdaRecord record = setTask("organization", "task");
        assertFalse(record.getIsDeleted());
        assertFalse(lambdaStore.get(record.getOrganizationName(), record.getTaskId()).orElseThrow().getIsDeleted());

        record = lambdaStore.markDeleted(record.getOrganizationName(), record.getTaskId());
        assertTrue(record.getIsDeleted());
        assertTrue(lambdaStore.get(record.getOrganizationName(), record.getTaskId()).orElseThrow().getIsDeleted());

        record = setTask(record.getOrganizationName(), record.getTaskId());
        assertFalse(record.getIsDeleted());
        assertFalse(lambdaStore.get(record.getOrganizationName(), record.getTaskId()).orElseThrow().getIsDeleted());
    }

    @Test
    public void testCheckLoops() throws Exception {
        // Conflict with itself
        assertTrue(lambdaStore.checkLoops("org1", "task1", ImmutableSet.of("queue1"), ImmutableSet.of("queue1")).isPresent());
        assertFalse(lambdaStore.checkLoops("org1", "task1", ImmutableSet.of("queue1"), ImmutableSet.of("queue2")).isPresent());
        setTask("org1", "task1", "queue1", "queue2");

        // No conflict if in other organization
        assertFalse(lambdaStore.checkLoops("otherOrg", "task2", ImmutableSet.of("queue2"), ImmutableSet.of("queue1")).isPresent());
        // Conflict with task1
        assertTrue(lambdaStore.checkLoops("org1", "task2", ImmutableSet.of("queue2"), ImmutableSet.of("queue1")).isPresent());
        // Second task chaining first
        assertFalse(lambdaStore.checkLoops("org1", "task2", ImmutableSet.of("queue2"), ImmutableSet.of("queue3")).isPresent());
        setTask("org1", "task2", "queue2", "queue3");

        // Third task conflict with first
        assertTrue(lambdaStore.checkLoops("org1", "task3", ImmutableSet.of("queue3"), ImmutableSet.of("queue1")).isPresent());
        // Conflict on update
        assertTrue(lambdaStore.checkLoops("org1", "task2", ImmutableSet.of("queue2"), ImmutableSet.of("queue1")).isPresent());
    }

    @Test
    public void testLock() throws Exception {
        try (AutoCloseable c1 = lambdaStore.acquireLock("org1", "task1").orElseThrow()) {
            log.info("org1 task1 locked");
            try (AutoCloseable c2 = lambdaStore.acquireLock("org1", "task2").orElseThrow()) {
                log.info("org1 task2 locked");
                try (AutoCloseable c3 = lambdaStore.acquireLock("org2", "task1").orElseThrow()) {
                    log.info("org2 task1 locked");
                    assertFalse(lambdaStore.acquireLock("org1", "task1").isPresent());
                    assertFalse(lambdaStore.acquireLock("org1", "task2").isPresent());
                    assertFalse(lambdaStore.acquireLock("org2", "task1").isPresent());
                }
            }
        }
        lambdaStore.acquireLock("org1", "task1").orElseThrow().close();
        lambdaStore.acquireLock("org1", "task2").orElseThrow().close();
        lambdaStore.acquireLock("org2", "task1").orElseThrow().close();
    }

    private LambdaRecord setTask(String organizationName, String taskId) {
        return setTask(organizationName, taskId, "queue" + UUID.randomUUID(), "queue" + UUID.randomUUID());
    }

    private LambdaRecord setTask(String organizationName, String taskId, String inputQueueName, String outputQueueName) {
        return lambdaStore.set(
                organizationName,
                taskId,
                "user" + UUID.randomUUID(),
                ImmutableSet.of(inputQueueName),
                ImmutableSet.of(outputQueueName),
                Optional.of("http://example.com/" + UUID.randomUUID()));
    }
}
