// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.runner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.SQSBatchResponseBuilder;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.dataspray.runner.dto.Request;
import io.dataspray.runner.dto.sqs.SqsMessage;
import io.dataspray.runner.dto.sqs.SqsRequest;
import io.dataspray.runner.dto.web.HttpRequest;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.HttpResponseException;
import io.dataspray.runner.util.GsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class Entrypoint implements RequestStreamHandler {

    private final Pattern sqsArnPattern = Pattern.compile("customer-(?<customer>[^-]+)-(?<queue>.+)");

    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        Request event = GsonUtil.get().fromJson(new InputStreamReader(input), Request.class);
        Object response = handleRequest(event, context);
        try (var writer = new OutputStreamWriter(output)) {
            GsonUtil.get().toJson(response, writer);
        }
    }

    /**
     * Entry point for the Lambda Function.
     */
    public Object handleRequest(Request event, Context context) {
        try {
            if (event.isSqsRequest()) {
                return handleSqsEvent(event);
            } else if (event.isHttpRequest()) {
                return handleHttpRequest(event);
            } else {
                throw new IllegalArgumentException("Unsupported event type: " + event.getClass());
            }
        } finally {
            StateManagerFactoryImpl.get()
                    .ifPresent(StateManagerFactory::closeAll);
        }
    }

    /**
     * Handle an SQS event containing one or more messages.
     */
    private SQSBatchResponse handleSqsEvent(SqsRequest event) {
        List<SQSBatchResponse.BatchItemFailure> failures = Lists.newArrayList();
        SQSBatchResponseBuilder responseBuilder = SQSBatchResponse.builder();

        for (SqsMessage msg : event.getRecords()) {
            try {
                Matcher matcher = sqsArnPattern.matcher(msg.getEventSourceArn());
                if (!matcher.matches()) {
                    throw new RuntimeException("Failed to determine source queue from ARN:" + msg.getEventSourceArn());
                }

                String messageKey = msg.getAttributes().get("MessageGroupId");
                if (Strings.isNullOrEmpty(messageKey)) {
                    throw new RuntimeException("SQS message does not have a message group id used as a message key");
                }

                String messageId = msg.getAttributes().get("MessageDeduplicationId");
                if (Strings.isNullOrEmpty(messageId)) {
                    throw new RuntimeException("SQS message does not have a message deduplication id used as a message id");
                }

                this.stream(new MessageMetadata(
                                StoreType.DATASPRAY,
                                matcher.group("customer"),
                                matcher.group("queue"),
                                messageKey,
                                messageId),
                        msg.getBody(),
                        RawCoordinatorImpl.get());
            } catch (Throwable th) {
                log.error("Failed to process SQS message", th);
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(msg.getMessageId()).build());
            }
        }

        return responseBuilder.withBatchItemFailures(failures).build();
    }

    /**
     * Handle an HTTP request from Function URL.
     */
    private HttpResponse handleHttpRequest(HttpRequest request) {
        HttpResponse response;
        try {
            response = web(request, RawCoordinatorImpl.get());
        } catch (HttpResponseException ex) {
            response = ex.getResponse();
        }
        log.info("{} {} {} {} {}",
                request.getRequestContext().getHttp().getMethod(),
                request.getRawPath(),
                response.getStatusCode(),
                response.getBody().length(),
                request.getRequestContext().getHttp().getUserAgent());
        return response;
    }

    protected void stream(MessageMetadata metadata, String data, RawCoordinator coordinator) {
        throw new RuntimeException("No handler defined for SQS events");
    }

    protected HttpResponse web(HttpRequest request, RawCoordinator coordinator) {
        throw new RuntimeException("No handler defined for web endpoints");
    }
}
