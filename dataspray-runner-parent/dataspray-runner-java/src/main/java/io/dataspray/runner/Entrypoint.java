// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.runner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.SQSBatchResponseBuilder;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class Entrypoint implements RequestHandler<Request, Object> {

    private final Pattern sqsArnPattern = Pattern.compile("customer-(?<customer>[^-]+)-(?<queue>.+)");

    public Object handleRequest(Request event, Context context) {

        if (event.isSqsRequest()) {
            return handleSQSEvent(event, context);
        } else if (event.isHttpRequest()) {
            return handleHttpRequest(event, context);
        } else {
            throw new IllegalArgumentException("Unsupported event type: " + event.getClass());
        }
    }

    private SQSBatchResponse handleSQSEvent(SqsRequest event, Context context) {
        SQSBatchResponseBuilder responseBuilder = SQSBatchResponse.builder();

        for (SQSMessage msg : event.getRecords()) {
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

    private HttpResponse handleHttpRequest(HttpRequest event, Context context) {
        TODO
    }

    public abstract void processSqsEvent(MessageMetadata metadata, String data, RawCoordinator coordinator);

    public abstract void processFunctionUrl(MessageMetadata metadata, String data, RawCoordinator coordinator);
}
