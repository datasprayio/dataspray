/*
 * Copyright 2023 Matus Faro
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

package io.dataspray.stream.ingest;

import io.dataspray.store.AccountStore;
import io.dataspray.store.AccountStore.StreamMetadata;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.EtlStore;
import io.dataspray.store.QueueStore;
import io.dataspray.web.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.io.InputStream;
import java.util.Optional;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

@Slf4j
@ApplicationScoped
public class IngestResource extends AbstractResource implements IngestApi {
    /** Limited by SQS max message size */
    public static final int MESSAGE_MAX_BYTES = 256 * 1024;

    @Inject
    AccountStore accountStore;
    @Inject
    QueueStore queueStore;
    @Inject
    EtlStore etlStore;
    @Inject
    CustomerLogger customerLog;

    @Override
    @SneakyThrows
    public void message(String accountId, String targetId, InputStream messageInputStream) {
        // Billing
        StreamMetadata streamMetadata = accountStore.authorizeStreamPut(
                accountId,
                targetId,
                getAuthKey());

        // Read message
        byte[] messageBytes = messageInputStream.readNBytes(MESSAGE_MAX_BYTES);
        if (messageInputStream.readNBytes(1).length > 0) {
            throw new ClientErrorException(Response.Status.REQUEST_ENTITY_TOO_LARGE);
        }
        messageInputStream.close();

        // Submit message to queue for stream processing
        MediaType mediaType = Optional.ofNullable(headers.getMediaType())
                .orElseGet(() -> {
                    customerLog.warn("Message for stream " + targetId + " missing media type", accountId);
                    return MediaType.APPLICATION_OCTET_STREAM_TYPE;
                });
        try {
            queueStore.submit(accountId, targetId, messageBytes, mediaType);
        } catch (QueueDoesNotExistException ex) {
            queueStore.createQueue(accountId, targetId);
            queueStore.submit(accountId, targetId, messageBytes, mediaType);
        }

        // Submit message to S3 for later batch processing
        if (streamMetadata.getRetentionOpt().isPresent()) {
            // Only JSON supported for now
            if (APPLICATION_JSON_TYPE.equals(mediaType)) {
                etlStore.putRecord(accountId, targetId, messageBytes, streamMetadata.getRetentionOpt().get());
            } else {
                customerLog.warn("Message for stream " + targetId + " requires " + APPLICATION_JSON + ", skipping ETL", accountId);
            }
        }
    }
}
