package io.dataspray.stream.ingest;

import io.dataspray.web.resource.AbstractResource;
import io.dataspray.store.AccountStore;
import io.dataspray.store.AccountStore.StreamMetadata;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.EtlStore;
import io.dataspray.store.QueueStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.io.InputStream;

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
        try {
            queueStore.submit(accountId, targetId, messageBytes, headers.getMediaType());
        } catch (QueueDoesNotExistException ex) {
            queueStore.createQueue(accountId, targetId);
            queueStore.submit(accountId, targetId, messageBytes, headers.getMediaType());
        }

        // Submit message to S3 for later batch processing
        if (streamMetadata.getRetentionOpt().isPresent()) {
            // Only JSON supported for now
            if (APPLICATION_JSON_TYPE.equals(headers.getMediaType())) {
                etlStore.putRecord(accountId, targetId, messageBytes, streamMetadata.getRetentionOpt().get());
            } else {
                customerLog.warn("Message for stream " + targetId + " requires " + APPLICATION_JSON + ", skipping ETL", accountId);
            }
        }
    }
}
