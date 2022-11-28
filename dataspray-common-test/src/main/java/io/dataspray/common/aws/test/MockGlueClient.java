package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.quarkus.arc.Priority;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mockito.Mockito;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.AlreadyExistsException;
import software.amazon.awssdk.services.glue.model.CreateDatabaseRequest;
import software.amazon.awssdk.services.glue.model.CreateDatabaseResponse;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.glue.model.GetDatabaseResponse;
import software.amazon.awssdk.services.glue.model.InvalidInputException;

import javax.annotation.Nonnull;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static io.dataspray.common.aws.test.MockIamClient.SDK_200;
import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockGlueClient {
    public static final String MOCK_GLUE_DATABASES = "mock-glue-databases";

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.glue.mock.enable", stringValue = "true")
    @Named(MOCK_GLUE_DATABASES)
    public ConcurrentMap<String, GlueDatabase> getMockDatabases() {
        return Maps.newConcurrentMap();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.glue.mock.enable", stringValue = "true")
    public GlueClient getGlueClient(@Named(MOCK_GLUE_DATABASES) ConcurrentMap<String, GlueDatabase> dbs) {
        GlueClient mock = Mockito.mock(GlueClient.class);

        when(mock.createDatabase(Mockito.<CreateDatabaseRequest>any()))
                .thenAnswer(invocation -> {
                    CreateDatabaseRequest request = invocation.getArgument(0, CreateDatabaseRequest.class);
                    validateCatalogId(request.catalogId());
                    if (dbs.putIfAbsent(request.databaseInput().name(), new GlueDatabase(request.databaseInput().name())) != null) {
                        throw AlreadyExistsException.builder().message("Database already exists").build();
                    }
                    return CreateDatabaseResponse.builder()
                            .sdkHttpResponse(SDK_200)
                            .build();
                });

        when(mock.getDatabase(Mockito.<GetDatabaseRequest>any()))
                .thenAnswer(invocation -> {
                    GetDatabaseRequest request = invocation.getArgument(0, GetDatabaseRequest.class);
                    validateCatalogId(request.catalogId());
                    GlueDatabase db = dbs.get(request.name());
                    if (db == null) {
                        throw EntityNotFoundException.builder().message("Database doesn't exist").build();
                    }
                    return GetDatabaseResponse.builder()
                            .database(Database.builder()
                                    .catalogId(awsAccountId)
                                    .name(db.getName())
                                    .build())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });

        return mock;
    }

    private void validateCatalogId(String catalogId) {
        if (!awsAccountId.equals(catalogId)) {
            throw InvalidInputException.builder().message("Catalog id invalid").build();
        }
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    public static class GlueDatabase {
        @Nonnull
        String name;
    }

    public static class TestProfile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.glue.mock.enable", "true");
        }
    }
}
