package io.dataspray.common.aws.test;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AwsTestProfile extends CombinedTestProfile {

    public AwsTestProfile() {
        super(MockDynamoDbClient.Profile.class,
                MockS3Client.Profile.class,
                MockLambdaClient.Profile.class,
                MockIamClient.Profile.class,
                MockSqsClient.Profile.class,
                MockFirehoseClient.Profile.class,
                MockGlueClient.Profile.class,
                MockAthenaClient.Profile.class,
                MockCognitoClient.Profile.class,
                SingleTenantAccountStoreProfile.class,
                TestAwsCredentialsProfile.class);
    }
}
