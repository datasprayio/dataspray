package io.dataspray.stream.ingest;

import com.amazonaws.util.StringInputStream;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.dataspray.common.aws.test.AwsTestProfile;
import io.dataspray.common.aws.test.MockFirehoseClient.FirehoseQueue;
import io.dataspray.common.json.GsonUtil;
import io.dataspray.web.resource.AbstractResource;
import io.dataspray.store.AccountStore;
import io.dataspray.store.FirehoseS3AthenaEtlStore;
import io.dataspray.store.SingleTenantAccountStore;
import io.dataspray.store.SqsQueueStore;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.firehose.model.Record;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static io.dataspray.common.aws.test.MockFirehoseClient.MOCK_FIREHOSE_QUEUES;
import static io.dataspray.store.FirehoseS3AthenaEtlStore.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@QuarkusTest
@TestProfile(AwsTestProfile.class)
public class IngestTest {

    @Inject
    IngestResource resource;
    @Inject
    Gson gson;
    @Inject
    @Named(GsonUtil.PRETTY_PRINT)
    Gson gsonPrettyPrint;
    @Inject
    SqsClient sqsClient;
    @Inject
    SqsQueueStore sqsQueueStore;
    @Inject
    @Named(MOCK_FIREHOSE_QUEUES)
    Function<String, FirehoseQueue> firehoseQueueSupplier;
    @Inject
    SingleTenantAccountStore singleTenantAccountStore;

    @InjectMock
    HttpHeaders httpHeaders;

    @BeforeEach
    public void setup() {
        Mockito.when(httpHeaders.getRequestHeader(AbstractResource.API_TOKEN_HEADER_NAME))
                .thenReturn(ImmutableList.of(singleTenantAccountStore.accountApiKey));
        Mockito.when(httpHeaders.getMediaType())
                .thenReturn(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void test() throws Exception {
        String queueName = UUID.randomUUID().toString();
        ImmutableMap<String, String> bodyMap = ImmutableMap.of("user", "matus");
        String bodyJsonPretty = gsonPrettyPrint.toJson(bodyMap);
        String bodyJson = gson.toJson(bodyMap);
        try (StringInputStream bodyInputStream = new StringInputStream(bodyJsonPretty)) {
            resource.message(singleTenantAccountStore.accountId,
                    queueName,
                    bodyInputStream);
        }

        // Assert stream processing
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(sqsQueueStore.getAwsQueueUrl(singleTenantAccountStore.accountId, queueName))
                .maxNumberOfMessages(10).build()).messages();
        assertEquals(1, messages.size());
        assertEquals(bodyJsonPretty, messages.get(0).body());

        // Assert Firehose ETL
        Record bodyActualRecord = firehoseQueueSupplier.apply(FirehoseS3AthenaEtlStore.FIREHOSE_STREAM_NAME).getQueue().poll();
        assertNotNull(bodyActualRecord);
        Map<String, String> bodyActualRecordMap = gson.fromJson(bodyActualRecord.data().asUtf8String(), new TypeToken<Map<String, String>>() {
        }.getType());
        assertEquals(ImmutableMap.builder()
                        .putAll(bodyMap)
                        .put(ETL_PARTITION_KEY_RETENTION, AccountStore.EtlRetention.DEFAULT.name())
                        .put(ETL_PARTITION_KEY_ACCOUNT, singleTenantAccountStore.accountId)
                        .put(ETL_PARTITION_KEY_TARGET, queueName)
                        .build(),
                bodyActualRecordMap);
    }
}
