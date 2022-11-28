package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import io.quarkus.arc.Priority;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mockito.Mockito;
import software.amazon.awssdk.services.athena.AthenaClient;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Singleton;
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

    public static class TestProfile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.athena.mock.enable", "true");
        }
    }
}
