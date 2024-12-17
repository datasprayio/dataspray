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


import com.google.common.base.Strings;
import io.dataspray.common.json.GsonUtil;
import io.dataspray.singletable.DynamoTable;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.time.Duration;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static io.dataspray.singletable.TableType.Primary;

/**
 * <b>Job state for async processing.</b>
 * <p>
 * Intended for API Gateway to async invoke lambda and caller to poll for completion.
 * </p>
 */
public interface JobStore {

    /** Maximum Lambda invocation ttl with leeway */
    Duration SESSION_PENDING_TIMEOUT = Duration.ofHours(6);
    /** Maximum Lambda invocation ttl with leeway */
    Duration SESSION_PROCESSING_TIMEOUT = Duration.ofMinutes(16);
    /** Maximum time to fetch result after it's made available */
    Duration SESSION_RESULT_TTL = Duration.ofHours(1);

    Session createSession();

    Session startSession(String sessionId);

    Session success(String sessionId, Object result);

    Session failure(String sessionId, String errorStr);

    Optional<Session> check(String sessionId);

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "sessionId", rangePrefix = "session")
    @RegisterForReflection
    class Session {

        @NonNull
        String sessionId;

        @NonNull
        Boolean pending;

        @Nullable
        String resultStr;

        @Nullable
        String errorStr;

        @NonNull
        Long ttlInEpochSec;

        public SessionState getState() {
            if (pending) {
                return SessionState.PENDING;
            } else if (!Strings.isNullOrEmpty(resultStr)) {
                return SessionState.SUCCESS;
            } else if (!Strings.isNullOrEmpty(errorStr)) {
                return SessionState.FAILURE;
            } else {
                return SessionState.PROCESSING;
            }
        }

        public <T> T getResult(Class<T> clazz) {
            checkState(getState() == SessionState.SUCCESS, "No result yet for session %s", sessionId);
            return GsonUtil.get().fromJson(resultStr, clazz);
        }

        public String getError() {
            checkState(getState() == SessionState.FAILURE, "No error yet for session %s", sessionId);
            return errorStr;
        }
    }

    enum SessionState {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILURE
    }
}
