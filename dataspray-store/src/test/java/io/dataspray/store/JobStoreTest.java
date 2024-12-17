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

import io.dataspray.common.test.AbstractTest;
import io.dataspray.common.test.aws.MotoInstance;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.JobStore.Session;
import io.dataspray.store.JobStore.SessionState;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class JobStoreTest extends AbstractTest {

    MotoInstance motoInstance;

    @Inject
    JobStore jobStore;

    @Value
    public static class Data {
        String a;
        long b;
    }

    @Test
    public void test() throws Exception {
        Session session1 = jobStore.createSession();
        Session session2 = jobStore.createSession();

        assertEquals(SessionState.PENDING, session1.getState());
        assertEquals(SessionState.PENDING, session2.getState());
        assertEquals(Optional.of(SessionState.PENDING), jobStore.check(session1.getSessionId()).map(Session::getState));

        assertEquals(SessionState.PROCESSING, jobStore.startSession(session1.getSessionId()).getState());
        assertEquals(SessionState.PROCESSING, jobStore.startSession(session2.getSessionId()).getState());
        assertEquals(Optional.of(SessionState.PROCESSING), jobStore.check(session1.getSessionId()).map(Session::getState));

        assertEquals(Optional.empty(), jobStore.check("non-existent"));

        Data data = new Data("a", 1);
        jobStore.success(session1.getSessionId(), data);

        assertResult(jobStore.check(session1.getSessionId()).orElseThrow(), data);
        assertEquals(Optional.of(SessionState.PROCESSING), jobStore.check(session2.getSessionId()).map(Session::getState));

        String errorCode = "some failure error code 123";
        jobStore.failure(session2.getSessionId(), errorCode);

        assertResult(jobStore.check(session1.getSessionId()).orElseThrow(), data);
        assertError(jobStore.check(session2.getSessionId()).orElseThrow(), errorCode);

        assertConditionFail(() -> jobStore.success(session1.getSessionId(), data));
        assertConditionFail(() -> jobStore.failure(session1.getSessionId(), errorCode));
        assertConditionFail(() -> jobStore.success(session2.getSessionId(), data));
        assertConditionFail(() -> jobStore.failure(session2.getSessionId(), errorCode));
        assertConditionFail(() -> jobStore.success("non-existent", data));
        assertConditionFail(() -> jobStore.failure("non-existent", errorCode));
    }

    void assertConditionFail(Runnable runnable) {
        try {
            runnable.run();
            fail();
        } catch (ConditionalCheckFailedException ex) {
            log.info("Expected exception", ex);
        }
    }

    <T> void assertResult(Session session, T expectedResult) {
        assertEquals(SessionState.SUCCESS, session.getState());
        assertEquals(expectedResult, session.getResult(expectedResult.getClass()));
    }

    <T> void assertError(Session session, String expectedErrorStr) {
        assertEquals(SessionState.FAILURE, session.getState());
        assertEquals(expectedErrorStr, session.getError());
    }
}
