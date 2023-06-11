package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.mockito.Mockito;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchResponse;
import software.amazon.awssdk.services.firehose.model.PutRecordRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordResponse;
import software.amazon.awssdk.services.firehose.model.Record;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockFirehoseClient {
    public static final String MOCK_FIREHOSE_QUEUES = "mock-firehose-queues";

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.firehose.mock.enable", stringValue = "true")
    @Named(MOCK_FIREHOSE_QUEUES)
    public Function<String, FirehoseQueue> getMockQueues() {
        ConcurrentMap<String, FirehoseQueue> queues = Maps.newConcurrentMap();
        return name -> queues.computeIfAbsent(name, name2 -> new FirehoseQueue(name2, Queues.newConcurrentLinkedQueue()));
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.firehose.mock.enable", stringValue = "true")
    public FirehoseClient getFirehoseClient(@Named(MOCK_FIREHOSE_QUEUES) Function<String, FirehoseQueue> queueSupplier) {
        FirehoseClient mock = Mockito.mock(FirehoseClient.class);

        when(mock.putRecord(Mockito.<PutRecordRequest>any()))
                .thenAnswer(invocation -> {
                    PutRecordRequest request = invocation.getArgument(0, PutRecordRequest.class);
                    queueSupplier.apply(request.deliveryStreamName())
                            .getQueue().add(request.record());
                    return PutRecordResponse.builder()
                            .recordId(UUID.randomUUID().toString()).build();
                });
        when(mock.putRecordBatch(Mockito.<PutRecordBatchRequest>any()))
                .thenAnswer(invocation -> {
                    PutRecordBatchRequest request = invocation.getArgument(0, PutRecordBatchRequest.class);
                    queueSupplier.apply(request.deliveryStreamName())
                            .getQueue()
                            .addAll(request.records());
                    return PutRecordBatchResponse.builder()
                            .failedPutCount(0)
                            .build();
                });

        return mock;
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    public static class FirehoseQueue {
        @Nonnull
        String name;
        @Nonnull
        Queue<Record> queue;
    }

    public static class Profile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.firehose.mock.enable", "true");
        }
    }
}
