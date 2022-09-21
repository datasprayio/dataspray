package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.quarkus.test.junit.QuarkusTestProfile;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class AwsTestProfile implements QuarkusTestProfile {

    @Inject
    DynamoDbClientTestProfile dynamoDbClientTestProfile;
    @Inject
    LambdaClientTestProfile lambdaClientTestProfile;
    @Inject
    S3ClientTestProfile s3ClientTestProfile;

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return ImmutableSet.<Class<?>>builder()
                .addAll(dynamoDbClientTestProfile.getEnabledAlternatives())
                .addAll(lambdaClientTestProfile.getEnabledAlternatives())
                .addAll(s3ClientTestProfile.getEnabledAlternatives())
                .build();
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return ImmutableMap.<String, String>builder()
                .putAll(dynamoDbClientTestProfile.getConfigOverrides())
                .putAll(lambdaClientTestProfile.getConfigOverrides())
                .putAll(s3ClientTestProfile.getConfigOverrides())
                .build();
    }
}
