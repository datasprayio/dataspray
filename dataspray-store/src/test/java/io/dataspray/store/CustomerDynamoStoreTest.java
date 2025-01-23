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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import io.dataspray.common.test.AbstractTest;
import io.dataspray.common.test.aws.MotoInstance;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.singletable.DynamoTable;
import io.dataspray.singletable.IndexSchema;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.util.IdUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class CustomerDynamoStoreTest extends AbstractTest {

    MotoInstance motoInstance;

    @Inject
    CustomerDynamoStore store;
    @Inject
    DynamoDbClient dynamo;
    @Inject
    IdUtil idUtil;
    @Inject
    LambdaDeployer lambdaDeployer;
    @Inject
    Gson gson;

    @Value
    @DynamoTable(type = Primary, partitionKeys = "someString", rangeKeys = {"someInt"}, rangePrefix = "data")
    @DynamoTable(type = Gsi, indexNumber = 1, partitionKeys = {"someString", "someInt"}, rangeKeys = {"someString"}, rangePrefix = "dataGsi")
    static class Data {
        @NonNull
        String someString;

        @NonNull
        int someInt;

        /** Collections are serialized as string due to type erasure */
        @NonNull
        String someListOfString;

        @Nullable
        String someNullable;

        @Nullable
        String someBlacklisted;
    }

    private static final TopicStore.Store DATA_DEFINITION = TopicStore.Store.builder()
            .keys(ImmutableSet.of(
                    TopicStore.Key.builder()
                            .type(Primary)
                            .indexNumber(0)
                            .pkParts(ImmutableList.of("someString"))
                            .skParts(ImmutableList.of("someInt"))
                            .rangePrefix("data")
                            .build(),
                    TopicStore.Key.builder()
                            .type(Gsi)
                            .indexNumber(1)
                            .pkParts(ImmutableList.of("someString", "someInt"))
                            .skParts(ImmutableList.of("someString"))
                            .rangePrefix("dataGsi")
                            .build()))
            .ttlInSec(1_000)
            .whitelist(ImmutableSet.of("someString", "someInt", "someListOfString"))
            .blacklist(ImmutableSet.of("someBlacklisted"))
            .build();

    @Test
    public void test() throws Exception {
        String orgName = idUtil.randomId();
        SingleTable singleTable = store.createTableIfNotExists(orgName, 0, 1);
        TableSchema<Data> primary = singleTable.parseTableSchema(Data.class);
        IndexSchema<Data> gsi = singleTable.parseGlobalSecondaryIndexSchema(1, Data.class);

        Data dataExpected = new Data(
                "test",
                1,
                gson.toJson(List.of("a", "b")),
                null,
                null);
        Map<String, Object> inputJson = Map.of(
                "someString", dataExpected.getSomeString(),
                "someInt", dataExpected.getSomeInt(),
                "someListOfString", List.of("a", "b"),
                // someNullable skipped
                "someBlacklisted", "this should be discarded");
        store.write(orgName, DATA_DEFINITION, inputJson);

        Optional<Data> dataPrimaryActual = primary.get()
                .key(Map.of(
                        "someString", inputJson.get("someString"),
                        "someInt", inputJson.get("someInt")))
                .executeGet(motoInstance.getDynamoClient());
        assertEquals(Optional.of(dataExpected), dataPrimaryActual);

        List<Data> dataGsiActual = gsi.query()
                .keyConditionsEqualsPrimaryKey(Map.of(
                        "someString", inputJson.get("someString"),
                        "someInt", inputJson.get("someInt")))
                .executeStream(motoInstance.getDynamoClient())
                .toList();
        assertEquals(List.of(dataExpected), dataGsiActual);
    }
}
