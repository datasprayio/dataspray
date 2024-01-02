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

package io.dataspray.authorizer;

import io.dataspray.common.json.GsonUtil;
import io.dataspray.common.test.aws.MotoInstance;
import io.dataspray.singletable.SingleTable;
import io.dataspray.store.SingleTableProvider;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Slf4j
@QuarkusIntegrationTest
class AuthorizerIT extends AuthorizerBase {

    MotoInstance motoInstance;

    private DynamoDbClient dynamo;
    private SingleTable singleTable;
    private KeygenUtil keygenUtil;

    @BeforeEach
    public void beforeEach() {
        this.keygenUtil = new KeygenUtil();
        this.dynamo = motoInstance.getDynamoClient();
        this.singleTable = SingleTable.builder()
                .tablePrefix(SingleTableProvider.TABLE_PREFIX_DEFAULT)
                .overrideGson(GsonUtil.get())
                .build();
        this.singleTable.createTableIfNotExists(dynamo, SingleTableProvider.LSI_COUNT, SingleTableProvider.GSI_COUNT);
    }

    @Override
    protected SingleTable getSingleTable() {
        return singleTable;
    }

    @Override
    protected DynamoDbClient getDynamo() {
        return dynamo;
    }

    @Override
    protected KeygenUtil getKeygenUtil() {
        return keygenUtil;
    }
}
