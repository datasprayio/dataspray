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

import io.dataspray.store.TopicStore.BatchRetention;
import lombok.Value;
import software.amazon.awssdk.services.glue.model.DataFormat;

import java.util.Optional;

public interface BatchStore {

    /**
     * @return Firehose sent Record ID
     */
    String putRecord(byte[] messageBytes);

    Optional<TableDefinition> getTableDefinition(String organizationName,
                                                 String topicName);

    void setTableDefinition(String organizationName,
                            String topicName,
                            DataFormat dataFormat,
                            String schemaDefinition,
                            BatchRetention retention);

    /**
     * Recalculate schema by inferring from S3 data.
     * Reads sample data files, merges all fields, and updates the Glue table.
     *
     * @return The inferred table definition
     * @throws IllegalArgumentException if topic doesn't have batch enabled or no data exists
     */
    TableDefinition recalculateTableDefinition(String organizationName,
                                                String topicName,
                                                BatchRetention retention);

    /**
     * List files in S3 for a topic.
     *
     * @param prefix Optional prefix to filter results (e.g., "year=2025/month=01/")
     * @param maxResults Maximum number of results (default 100, max 1000)
     * @param nextToken Continuation token for pagination
     * @return List of S3 objects with pagination token
     */
    FilesListResult listFiles(String organizationName,
                               String topicName,
                               BatchRetention retention,
                               String prefix,
                               int maxResults,
                               String nextToken);

    /**
     * Generate presigned URL for downloading a file.
     *
     * @param key S3 object key
     * @return Presigned URL and expiration time
     */
    PresignedUrl getFileDownloadUrl(String organizationName,
                                     String topicName,
                                     BatchRetention retention,
                                     String key);

    @Value
    class TableDefinition {
        String schema;
        DataFormat dataFormat;
    }

    @Value
    class FilesListResult {
        java.util.List<S3File> files;
        String nextToken;
    }

    @Value
    class S3File {
        String key;
        long size;
        java.time.Instant lastModified;
    }

    @Value
    class PresignedUrl {
        String url;
        java.time.Instant expiresAt;
    }
}
