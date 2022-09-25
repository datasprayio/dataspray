package io.dataspray.stream.ingest;

import com.amazonaws.util.StringInputStream;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import io.dataspray.common.aws.test.AwsTestProfile;
import io.dataspray.store.LimitlessBillingStore;
import io.dataspray.store.SqsQueueStore;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectMock;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import javax.inject.Inject;
import javax.ws.rs.core.HttpHeaders;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@QuarkusTest
@TestProfile(AwsTestProfile.class)
public class IngestTest {

    @Inject
    IngestResource resource;
    @Inject
    Gson gson;
    @Inject
    SqsClient sqsClient;
    @Inject
    SqsQueueStore sqsQueueStore;

    @InjectMock
    HttpHeaders httpHeaders;

    @BeforeEach
    public void setup() {
        Mockito.when(httpHeaders.getRequestHeader(IngestResource.API_TOKEN_HEADER_NAME))
                .thenReturn(ImmutableList.of(LimitlessBillingStore.ACCOUNT_API_KEY));
    }

    @Test
    public void test() throws Exception {
        String queueName = "my-queue";
        String body = gson.toJson(ImmutableMap.of("user", "matus"));
        resource.message(LimitlessBillingStore.ACCOUNT_ID,
                queueName,
                new StringInputStream(body));
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(sqsQueueStore.getQueueUrl(LimitlessBillingStore.ACCOUNT_ID, queueName))
                .maxNumberOfMessages(10).build()).messages();
        assertEquals(1, messages.size());
        assertEquals(body, messages.get(0).body());
    }
}
