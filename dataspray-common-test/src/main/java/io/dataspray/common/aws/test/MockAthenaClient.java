package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mockito.Mockito;
import software.amazon.awssdk.services.athena.AthenaClient;

import java.util.Map;

@ApplicationScoped
public class MockAthenaClient {

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.athena.mock.enable", stringValue = "true")
    public AthenaClient getAthenaClient() {
        AthenaClient mock = Mockito.mock(AthenaClient.class);

        // TODO

        return mock;
    }

    public static class Profile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.athena.mock.enable", "true");
        }
    }
}
