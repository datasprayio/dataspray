package io.dataspray.stream.ingest;

import com.google.common.base.Strings;
import io.dataspray.lambda.resource.AbstractResource;
import io.dataspray.store.BillingStore;
import io.dataspray.store.BillingStore.StreamMetadata;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.EtlStore;
import io.dataspray.store.QueueStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

@Slf4j
@ApplicationScoped
public class IngestResource extends AbstractResource implements IngestApi {
    /** Limited by SQS max message size */
    public static final int MESSAGE_MAX_BYTES = 256 * 1024;
    /** Can be supplied via header, query param */
    public static final String API_TOKEN_HEADER_NAME = "x-api-key";
    public static final String API_TOKEN_QUERY_NAME = "api_key";
    public static final String API_TOKEN_COOKIE_NAME = "x-api-key";
    public static final String API_TOKEN_AUTHORIZATION_TYPE = "bearer";

    @Inject
    BillingStore billingStore;
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
        StreamMetadata streamMetadata = billingStore.recordStreamEvent(
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

    private Optional<String> getAuthKey() {
        // First check api header
        return headers.getRequestHeader(API_TOKEN_HEADER_NAME).stream().findFirst()
                .filter(Predicate.not(Strings::isNullOrEmpty))
                // Then check authorization header
                .or(() -> {
                    List<String> authorizationHeaderValues = headers.getRequestHeader(HttpHeaders.AUTHORIZATION);
                    if (authorizationHeaderValues.size() != 2
                            || !API_TOKEN_AUTHORIZATION_TYPE.equalsIgnoreCase(authorizationHeaderValues.get(0))
                            || Strings.isNullOrEmpty(authorizationHeaderValues.get(1))) {
                        return Optional.empty();
                    }
                    return Optional.of(authorizationHeaderValues.get(1));
                })
                .or(() -> Optional.ofNullable(headers.getCookies().get(API_TOKEN_COOKIE_NAME))
                        .map(Cookie::getValue))
                // Then check query param
                .or(() -> Optional.ofNullable(uriInfo.getQueryParameters().get(API_TOKEN_QUERY_NAME))
                        .flatMap(values -> values.stream().findFirst()));
    }
}
