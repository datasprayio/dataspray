package io.dataspray.stream.ingest;

import com.google.common.base.Strings;
import io.dataspray.lambda.resource.AbstractResource;
import io.dataspray.store.BillingStore;
import io.dataspray.store.QueueStore;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@ApplicationScoped
public class IngestResource extends AbstractResource implements IngestApi {
    /** Can be supplied via header, query param */
    private static final String API_TOKEN_HEADER_NAME = "x-api-key";
    private static final String API_TOKEN_QUERY_NAME = "api_key";
    private static final String API_TOKEN_COOKIE_NAME = "x-api-key";
    private static final String API_TOKEN_AUTHORIZATION_TYPE = "bearer";

    @Inject
    BillingStore billingStore;
    @Inject
    QueueStore queueStore;

    @Override
    @SneakyThrows
    public void message(String accountId, String targetId, InputStream body) {
        // Billing
        if (billingStore.recordStreamEvent(
                accountId,
                targetId,
                getAuthKey())) {
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
