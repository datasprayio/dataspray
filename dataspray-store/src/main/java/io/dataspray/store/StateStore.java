/*
 * Copyright 2025 Matus Faro
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

import com.google.common.collect.ImmutableMap;
import io.dataspray.store.util.WithCursor;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 * Interface for managing state entries stored in DynamoDB.
 * State entries are stored with pk (composite key) and sk = "state".
 */
public interface StateStore {

    @Value
    class StateEntry {
        @NonNull String[] keyParts;
        @NonNull String mergedKey;
        @NonNull ImmutableMap<String, Object> attributes;
        @NonNull Optional<Long> ttlInEpochSec;
    }

    /**
     * List state entries with optional filtering by key prefix.
     *
     * @param organizationName Organization name
     * @param keyPrefix        Optional key prefix filter (e.g., ["task", "processorId"])
     * @param cursor           Optional pagination cursor
     * @param limit            Maximum number of entries to return
     * @return List of state entries with optional next cursor
     */
    WithCursor<List<StateEntry>> listState(
            String organizationName,
            Optional<String[]> keyPrefix,
            Optional<String> cursor,
            int limit
    );

    /**
     * Get a specific state entry by key.
     *
     * @param organizationName Organization name
     * @param keyParts         Composite key identifying the state entry
     * @return State entry if found
     */
    Optional<StateEntry> getState(
            String organizationName,
            String[] keyParts
    );

    /**
     * Create or update a state entry.
     *
     * @param organizationName Organization name
     * @param keyParts         Composite key identifying the state entry
     * @param attributes       State attributes to store
     * @param ttlInSec         Optional TTL in seconds from now
     * @return Created or updated state entry
     */
    StateEntry upsertState(
            String organizationName,
            String[] keyParts,
            ImmutableMap<String, Object> attributes,
            Optional<Long> ttlInSec
    );

    /**
     * Delete a state entry.
     *
     * @param organizationName Organization name
     * @param keyParts         Composite key identifying the state entry to delete
     */
    void deleteState(
            String organizationName,
            String[] keyParts
    );
}
