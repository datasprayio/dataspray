package io.dataspray.stream.ingest;

import io.dataspray.lambda.resource.AbstractResource;
import io.dataspray.store.BillingStore;
import io.dataspray.store.QueueStore;
import io.dataspray.stream.server.IngestApi;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import java.io.InputStream;

import static io.dataspray.store.BillingStore.IMPL_LIMITLESS;

@Slf4j
@ApplicationScoped
public class IngestResource extends AbstractResource implements IngestApi {

    @Inject
    @Named(IMPL_LIMITLESS)
    BillingStore billingStore;
    @Inject
    QueueStore queueStore;

    @Override
    @SneakyThrows
    public void message(String accountId, String targetId, InputStream body) {
        // TODO authentication

        // Billing
        if (billingStore.recordStreamEvent(accountId)) {
            throw new ClientErrorException(Response.Status.PAYMENT_REQUIRED);
        }

        // Submit message
        try {
            queueStore.submit(accountId, targetId, body.readAllBytes());
        } catch (QueueDoesNotExistException ex) {
            queueStore.createQueue(accountId, targetId);
            queueStore.submit(accountId, targetId, body.readAllBytes());
        }
    }
}
