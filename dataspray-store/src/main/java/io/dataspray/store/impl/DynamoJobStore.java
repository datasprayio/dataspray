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

package io.dataspray.store.impl;

import com.google.gson.Gson;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.JobStore;
import io.dataspray.store.util.IdUtil;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class DynamoJobStore implements JobStore {

    @Inject
    DynamoDbClient dynamo;
    @Inject
    SingleTable singleTable;
    @Inject
    IdUtil idUtil;
    @Inject
    Gson gson;

    private TableSchema<Session> sessionSchema;

    @Startup
    void init() {
        sessionSchema = singleTable.parseTableSchema(Session.class);
    }

    @Override
    public Session createSession() {
        return sessionSchema.put()
                .item(new Session(idUtil.randomId(), true, null, null,
                        Instant.now().plus(SESSION_PENDING_TIMEOUT).getEpochSecond()))
                .executeGetNew(dynamo);
    }

    @Override
    public Session startSession(String sessionId) {
        return sessionSchema.update()
                .key(Map.of("sessionId", sessionId))
                .conditionExists()
                .conditionFieldEquals("pending", true)
                .conditionFieldNotExists("resultStr")
                .conditionFieldNotExists("errorStr")
                .set("pending", false)
                .set("ttlInEpochSec", Instant.now().plus(SESSION_PROCESSING_TIMEOUT).getEpochSecond())
                .executeGetUpdated(dynamo);
    }

    @Override
    public Session success(String sessionId, Object result) {
        return sessionSchema.update()
                .key(Map.of("sessionId", sessionId))
                .conditionExists()
                .conditionFieldEquals("pending", false)
                .conditionFieldNotExists("resultStr")
                .conditionFieldNotExists("errorStr")
                .set("resultStr", gson.toJson(result))
                .set("ttlInEpochSec", Instant.now().plus(SESSION_RESULT_TTL).getEpochSecond())
                .executeGetUpdated(dynamo);
    }

    @Override
    public Session failure(String sessionId, String errorStr) {
        return sessionSchema.update()
                .key(Map.of("sessionId", sessionId))
                .conditionExists()
                .conditionFieldEquals("pending", false)
                .conditionFieldNotExists("resultStr")
                .conditionFieldNotExists("errorStr")
                .set("errorStr", errorStr)
                .set("ttlInEpochSec", Instant.now().plus(SESSION_RESULT_TTL).getEpochSecond())
                .executeGetUpdated(dynamo);
    }

    @Override
    public Optional<Session> check(String sessionId) {
        return sessionSchema.get()
                .key(Map.of("sessionId", sessionId))
                .executeGet(dynamo);
    }
}
