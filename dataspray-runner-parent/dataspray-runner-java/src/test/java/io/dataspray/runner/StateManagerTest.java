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

package io.dataspray.runner;

import com.google.gson.Gson;
import io.dataspray.common.test.aws.MotoInstance;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import lombok.NonNull;
import lombok.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class StateManagerTest {

    MotoInstance motoInstance;
    private StateManager stateManager;

    @BeforeEach
    public void setUp() {
        CreateTableResponse createTableResponse = motoInstance.getDynamoClient().createTable(CreateTableRequest.builder()
                .tableName(UUID.randomUUID().toString())
                .keySchema(KeySchemaElement.builder().keyType(KeyType.HASH).attributeName("pk").build(),
                        KeySchemaElement.builder().keyType(KeyType.RANGE).attributeName("sk").build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
        stateManager = new DynamoStateManager(
                createTableResponse.tableDescription().tableName(),
                new Gson(),
                motoInstance.getDynamoClient(),
                new String[]{"someTask", "someMessageId"},
                Optional.of(Duration.ofDays(3)));
    }

    @Test
    public void test() throws Exception {
        assertEquals("", stateManager.getString("keyS"));

        stateManager.setString("keyS", "val1");
        assertEquals("val1", stateManager.getString("keyS"));

        stateManager.setString("keyS", "val2");

        stateManager.setStringSet("keySS1", Set.of("val1", "val2"));
        stateManager.addToStringSet("keySS1", "val3");
        stateManager.deleteFromStringSet("keySS1", "val2");
        stateManager.addToStringSet("keySS2", "val4");

        stateManager.setBoolean("keyBool1", true);
        stateManager.setBoolean("keyBool2", true);
        stateManager.setBoolean("keyBool2", false);

        stateManager.setNumber("keyN1", 1L);
        stateManager.setNumber("keyN2", 0.1d);
        stateManager.addToNumber("keyN1", 1);
        stateManager.addToNumber("keyN2", 1);
        stateManager.addToNumber("keyN3", 1);

        SomeData someData = new SomeData("val2", Set.of("val1", "val3"), null);
        stateManager.setJson("keyJ", someData);

        assertEquals("val2", stateManager.getString("keyS"));
        assertEquals(Set.of("val1", "val3"), stateManager.getStringSet("keySS1"));
        assertEquals(Set.of("val4"), stateManager.getStringSet("keySS2"));
        assertTrue(stateManager.getBoolean("keyBool1"));
        assertFalse(stateManager.getBoolean("keyBool2"));
        assertFalse(stateManager.getBoolean("keyBool3"));
        assertEquals(2L, stateManager.getNumber("keyN1").longValue());
        assertEquals(1.1d, stateManager.getNumber("keyN2").doubleValue());
        assertEquals(1L, stateManager.getNumber("keyN3").longValue());
        assertEquals(Optional.of(someData), stateManager.getJson("keyJ", someData.getClass()));
    }

    @Value
    public static class SomeData {
        @NonNull
        String keyS;
        @NonNull
        Set<String> keySS;
        Long keyN3;
    }
}
