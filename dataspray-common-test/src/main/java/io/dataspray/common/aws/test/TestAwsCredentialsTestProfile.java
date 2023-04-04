package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class TestAwsCredentialsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return ImmutableMap.of(
                "aws.credentials.accessKey", "dummy-access-key",
                "aws.credentials.secretKey", "dummy-secret-key");
    }
}
