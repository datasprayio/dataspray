/*
 * Copyright 2026 Matus Faro
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

import io.dataspray.common.test.AbstractTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.BatchStore;
import io.dataspray.store.TopicStore.BatchRetention;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class FirehoseS3AthenaBatchStoreTest extends AbstractTest {

    @Inject
    BatchStore batchStore;

    @Test
    public void testDownloadUrlRejectsKeysOutsideTopicPrefix() {
        String basePrefix = FirehoseS3AthenaBatchStore.ETL_BUCKET_TARGET_PREFIX
                .apply(BatchRetention.WEEK, "myorg", "mytopic")
                .replace("/year=!{timestamp:yyyy}", "")
                .replace("/month=!{timestamp:MM}", "")
                .replace("/day=!{timestamp:dd}", "")
                .replace("/hour=!{timestamp:HH}/", "");

        // Another org's key
        assertThrows(IllegalArgumentException.class, () ->
                batchStore.getFileDownloadUrl("myorg", "mytopic", BatchRetention.WEEK,
                        basePrefix.replace("organization=myorg", "organization=otherorg") + "/file.gz"));

        // Regression: prefix-confusion - topic "mytopic" must not authorize "mytopicother"
        assertThrows(IllegalArgumentException.class, () ->
                batchStore.getFileDownloadUrl("myorg", "mytopic", BatchRetention.WEEK,
                        basePrefix + "other/file.gz"));

        // Regression: org prefix-confusion likewise
        assertThrows(IllegalArgumentException.class, () ->
                batchStore.getFileDownloadUrl("myorg", "mytopic", BatchRetention.WEEK,
                        basePrefix.replace("organization=myorg", "organization=myorgother") + "/file.gz"));
    }

    @Test
    public void testDownloadUrlForKeyInsideTopicPrefix() {
        String key = FirehoseS3AthenaBatchStore.ETL_BUCKET_TARGET_PREFIX
                             .apply(BatchRetention.WEEK, "myorg", "mytopic")
                             .replace("/year=!{timestamp:yyyy}", "")
                             .replace("/month=!{timestamp:MM}", "")
                             .replace("/day=!{timestamp:dd}", "")
                             .replace("/hour=!{timestamp:HH}/", "")
                     + "/year=2026/file.gz";
        BatchStore.PresignedUrl url = batchStore.getFileDownloadUrl("myorg", "mytopic", BatchRetention.WEEK, key);
        assertTrue(url.getUrl().contains("file.gz"));
        assertFalse(url.getUrl().isBlank());
    }

    @Test
    public void testAthenaResultsPrefixHasSingleOrganizationSegment() {
        // Regression: the results prefix used to contain organization= twice
        String prefix = FirehoseS3AthenaBatchStore.ETL_BUCKET_ATHENA_RESULTS_PREFIX;
        int first = prefix.indexOf("organization=");
        int last = prefix.lastIndexOf("organization=");
        assertTrue(first >= 0);
        assertTrue(first == last, "Athena results prefix contains a duplicated organization segment: " + prefix);
        assertTrue(prefix.endsWith("/athena-results"));
    }
}
