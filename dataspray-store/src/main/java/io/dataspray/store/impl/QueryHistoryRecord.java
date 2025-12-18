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

package io.dataspray.store.impl;

import io.dataspray.singletable.DynamoTable;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NonNull;
import lombok.Value;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

/**
 * DynamoDB record for tracking query execution history.
 * <p>
 * Primary access pattern: Get queries by organization (sorted by submission time)
 * Secondary access pattern: Get query by execution ID (for authorization checks)
 */
@Value
@DynamoTable(type = Primary,
        partitionKeys = "organizationName",
        rangePrefix = "query",
        rangeKeys = {"submissionTime", "queryExecutionId"})
@DynamoTable(type = Gsi,
        indexNumber = 1,
        partitionKeys = "queryExecutionId",
        rangePrefix = "queryHistoryByQueryExecutionId")
@RegisterForReflection
public class QueryHistoryRecord {

    /**
     * Organization name (partition key for primary table).
     */
    @NonNull
    String organizationName;

    /**
     * Athena query execution ID (part of range key, GSI partition key).
     */
    @NonNull
    String queryExecutionId;

    /**
     * Query submission timestamp in epoch milliseconds (part of range key for sorting).
     */
    @NonNull
    Long submissionTime;

    /**
     * SQL query text.
     */
    @NonNull
    String sqlQuery;

    /**
     * Username who submitted the query.
     */
    @NonNull
    String username;

    /**
     * TTL timestamp in epoch seconds for automatic DynamoDB item expiration (7 days).
     */
    @NonNull
    Long ttlInEpochSec;
}
