package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import io.quarkus.arc.Priority;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import javax.annotation.Nonnull;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockSqsClient {
    public static final String MOCK_SQS_QUEUES = "mock-sqs-queues";

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.sqs.mock.enable", stringValue = "true")
    @Named(MOCK_SQS_QUEUES)
    public ConcurrentMap<String, SqsQueue> getMockQueues() {
        return Maps.newConcurrentMap();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.sqs.mock.enable", stringValue = "true")
    public SqsClient getSqsClient(@Named(MOCK_SQS_QUEUES) ConcurrentMap<String, SqsQueue> queues) {
        SqsClient mock = Mockito.mock(SqsClient.class);

        when(mock.createQueue(Mockito.<CreateQueueRequest>any()))
                .thenAnswer(invocation -> {
                    CreateQueueRequest request = invocation.getArgument(0, CreateQueueRequest.class);
                    SqsQueue queue = new SqsQueue(request.queueName(), Queues.newConcurrentLinkedQueue());
                    queues.put(request.queueName(), queue);
                    return CreateQueueResponse.builder().build();
                });
        when(mock.sendMessage(Mockito.<SendMessageRequest>any()))
                .thenAnswer(invocation -> {
                    SendMessageRequest request = invocation.getArgument(0, SendMessageRequest.class);
                    SqsQueue queue = queues.get(getQueueName(request.queueUrl()));
                    if (queue == null) {
                        throw QueueDoesNotExistException.builder().build();
                    }
                    queue.getQueue().add(request);
                    return SendMessageResponse.builder().build();
                });
        when(mock.purgeQueue(Mockito.<PurgeQueueRequest>any()))
                .thenAnswer(invocation -> {
                    PurgeQueueRequest request = invocation.getArgument(0, PurgeQueueRequest.class);
                    SqsQueue queue = queues.get(getQueueName(request.queueUrl()));
                    if (queue == null) {
                        throw QueueDoesNotExistException.builder().build();
                    }
                    queue.getQueue().clear();
                    return PurgeQueueResponse.builder().build();
                });
        when(mock.deleteQueue(Mockito.<DeleteQueueRequest>any()))
                .thenAnswer(invocation -> {
                    DeleteQueueRequest request = invocation.getArgument(0, DeleteQueueRequest.class);
                    SqsQueue queue = queues.remove(getQueueName(request.queueUrl()));
                    if (queue == null) {
                        throw QueueDoesNotExistException.builder().build();
                    }
                    return DeleteQueueResponse.builder().build();
                });
        when(mock.receiveMessage(Mockito.<ReceiveMessageRequest>any()))
                .thenAnswer(invocation -> {
                    ReceiveMessageRequest request = invocation.getArgument(0, ReceiveMessageRequest.class);
                    SqsQueue queue = queues.get(getQueueName(request.queueUrl()));
                    if (queue == null) {
                        throw QueueDoesNotExistException.builder().build();
                    }
                    int pollCount = Optional.ofNullable(request.maxNumberOfMessages()).orElse(1);
                    ImmutableList.Builder<Message> messagesBuilder = ImmutableList.builder();
                    do {
                        SendMessageRequest sendMessageRequest = queue.getQueue().poll();
                        if (sendMessageRequest == null) {
                            break;
                        }
                        messagesBuilder.add(Message.builder()
                                .messageId(UUID.randomUUID().toString())
                                .messageAttributes(sendMessageRequest.messageAttributes())
                                .body(sendMessageRequest.messageBody()).build());
                        pollCount--;
                    } while (pollCount > 0);
                    return ReceiveMessageResponse.builder()
                            .messages(messagesBuilder.build()).build();
                });

        return mock;
    }

    private String getQueueName(String queueUrl) {
        return queueUrl.substring(queueUrl.lastIndexOf("/") + 1);
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    public static class SqsQueue {
        @Nonnull
        String name;
        @Nonnull
        Queue<SendMessageRequest> queue;
    }

    public static class TestProfile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.sqs.mock.enable", "true");
        }
    }
}