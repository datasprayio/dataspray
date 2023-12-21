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

import io.dataspray.store.BatchStore;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.StreamStore;
import io.dataspray.store.TargetStore;
import io.dataspray.store.TargetStore.Stream;
import io.dataspray.store.TargetStore.Target;
import io.dataspray.web.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

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
    TargetStore targetStore;
    @Inject
    StreamStore streamStore;
    @Inject
    BatchStore batchStore;
    @Inject
    CustomerLogger customerLog;

    /**
     * <b>Ingest a message.</b>
     * <p>Receives a message and fan-out to stream and/or batch processing destinations</p>
     * <p>This is a hot-path as every message goes through here.</p>
     */
    @Override
    @SneakyThrows
    public void message(String organizationName, String targetName, InputStream messageInputStream) {

        // Fetch target definition
        // We assume we are already authenticated and authorized to ingest since we are behind API Gateway
        Target target = targetStore.getTarget(organizationName, targetName, true)
                // If target is not found and default targets are disabled, throw not found
                .orElseThrow(() -> {
                    customerLog.warn("Dropping message for undefined stream " + targetName, organizationName);
                    return new ClientErrorException(Response.Status.NOT_FOUND);
                });

        // Read message
        byte[] messageBytes = messageInputStream.readNBytes(MESSAGE_MAX_BYTES);
        if (messageInputStream.readNBytes(1).length > 0) {
            customerLog.warn("Dropping message for stream " + targetName + " that is too large (max " + MESSAGE_MAX_BYTES + " bytes)", organizationName);
            throw new ClientErrorException(Response.Status.REQUEST_ENTITY_TOO_LARGE);
        }
        messageInputStream.close();

        // Detect media type, needed for both stream and batch processing
        MediaType mediaType = Optional.ofNullable(headers.getMediaType())
                .orElseGet(() -> {
                    customerLog.warn("Message for stream " + targetName + " missing media type", organizationName);
                    return MediaType.APPLICATION_OCTET_STREAM_TYPE;
                });

        // Submit message to all streams for stream processing
        for (Stream stream : target.getStreams()) {
            streamStore.submit(organizationName, targetName, messageBytes, mediaType);
        }

        // Submit message for batch processing
        if (target.getBatch().isPresent()) {
            // Only JSON supported for now
            if (APPLICATION_JSON_TYPE.equals(mediaType)) {
                batchStore.putRecord(organizationName, targetName, messageBytes, target.getBatch().get().getRetention());
            } else {
                customerLog.warn("Message for stream " + targetName + " requires " + APPLICATION_JSON + ", skipping ETL", organizationName);
            }
        }
    }
}
