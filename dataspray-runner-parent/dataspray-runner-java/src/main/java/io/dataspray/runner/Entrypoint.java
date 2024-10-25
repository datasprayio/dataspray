// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.runner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.SQSBatchResponseBuilder;
import io.dataspray.runner.dto.Request;
import io.dataspray.runner.dto.sqs.SqsMessage;
import io.dataspray.runner.dto.sqs.SqsRequest;
import io.dataspray.runner.dto.web.HttpRequest;
import io.dataspray.runner.dto.web.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class Entrypoint implements RequestHandler<Request, Object> {

    private final Pattern sqsArnPattern = Pattern.compile("customer-(?<customer>[^-]+)-(?<queue>.+)");

    /**
     * Entry point for the Lambda Function.
     */
    public Object handleRequest(Request event, Context context) {
        if (event.isSqsRequest()) {
            return handleSqsEvent(event);
        } else if (event.isHttpRequest()) {
            return handleHttpRequest(event);
        } else {
            throw new IllegalArgumentException("Unsupported event type: " + event.getClass());
        }
    }

    /**
     * Handle an SQS event containing one or more messages.
     */
    private SQSBatchResponse handleSqsEvent(SqsRequest event) {
        SQSBatchResponseBuilder responseBuilder = SQSBatchResponse.builder();

        for (SqsMessage msg : event.getRecords()) {
            try {
                Matcher matcher = sqsArnPattern.matcher(msg.getEventSourceArn());
                if (!matcher.matches()) {
                    log.error("Failed to determine source queue from ARN {}", msg.getEventSourceArn());
                }
                this.processSqsEvent(new MessageMetadata(
                                StoreType.DATASPRAY,
                                matcher.group("customer"),
                                matcher.group("queue")),
                        msg.getBody(),
                        RawCoordinatorImpl.get());
            } catch (Throwable th) {
                responseBuilder.build();
            }
        }

        return responseBuilder.build();
    }

    /**
     * Handle an HTTP request from Function URL.
     */
    private HttpResponse handleHttpRequest(HttpRequest request) {
        return handleWebRequest(request, HttpResponse.builder());
    }

    protected void processSqsEvent(MessageMetadata metadata, String data, RawCoordinator coordinator) {
        throw new RuntimeException("No handler defined for SQS events");
    }

    protected HttpResponse handleWebRequest(HttpRequest request, HttpResponse.HttpResponseBuilder responseBuilder) {
        throw new RuntimeException("No handler defined for web endpoints");
    }
}
