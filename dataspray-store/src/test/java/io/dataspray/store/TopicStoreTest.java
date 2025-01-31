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
import io.dataspray.store.TopicStore.Batch;
import io.dataspray.store.TopicStore.Topic;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.Optional;

import static io.dataspray.store.TopicStore.DEFAULT_ALLOW_UNDEFINED_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@QuarkusTest
@QuarkusTestResource(MotoLifecycleManager.class)
public class TopicStoreTest extends AbstractTest {

    MotoInstance motoInstance;

    @Inject
    TopicStore topicStore;

    @Test
    public void test() throws Exception {
        assertEquals(DEFAULT_ALLOW_UNDEFINED_TOPIC, topicStore.getTopic("org1", "topic1", true));

        topicStore.updateDefaultTopic("org1", Optional.empty(), false, Optional.empty());
        assertEquals(Optional.empty(), topicStore.getTopic("org1", "topic1", true));

        Topic topicDefault = Topic.builder()
                .batch(Batch.builder()
                        .retention(TopicStore.BatchRetention.WEEK).build())
                .streams(List.of())
                .build();
        topicStore.updateDefaultTopic("org1", Optional.of(topicDefault), true, Optional.empty());
        assertEquals(Optional.of(topicDefault), topicStore.getTopic("org1", "topic1", true));
        assertEquals(DEFAULT_ALLOW_UNDEFINED_TOPIC, topicStore.getTopic("org2", "topic1", true));

        Topic topic1 = Topic.builder()
                .batch(Batch.builder()
                        .retention(TopicStore.BatchRetention.THREE_YEARS).build())
                .build();
        TopicStore.Topics lastUpdate = topicStore.updateTopic("org1", "topic1", topic1, Optional.empty());
        assertEquals(Optional.of(topic1), topicStore.getTopic("org1", "topic1", true));
        assertEquals(Optional.of(topicDefault), topicStore.getTopic("org1", "topic2", true));

        Topic topic2 = Topic.builder()
                .batch(Batch.builder()
                        .retention(TopicStore.BatchRetention.DAY).build())
                .build();
        try {
            topicStore.updateTopic("org1", "topic2", topic2, Optional.of(lastUpdate.getVersion() - 1));
            fail();
        } catch (ConditionalCheckFailedException ignored) {
            // Expected
        }
        topicStore.updateTopic("org1", "topic2", topic2, Optional.of(lastUpdate.getVersion()));
        assertEquals(Optional.of(topic1), topicStore.getTopic("org1", "topic1", true));
        assertEquals(Optional.of(topic2), topicStore.getTopic("org1", "topic2", true));
        assertEquals(Optional.of(topicDefault), topicStore.getTopic("org1", "topic3", true));
    }
}
