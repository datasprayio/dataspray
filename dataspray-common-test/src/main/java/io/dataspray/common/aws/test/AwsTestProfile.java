package io.dataspray.common.aws.test;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AwsTestProfile extends CombinedTestProfile {

    public AwsTestProfile() {
        super(MockDynamoDbClient.TestProfile.class,
                MockS3Client.TestProfile.class,
                MockLambdaClient.TestProfile.class,
                MockIamClient.TestProfile.class,
                MockSqsClient.TestProfile.class,
                MockFirehoseClient.TestProfile.class,
                MockGlueClient.TestProfile.class,
                MockAthenaClient.TestProfile.class,
                TestAwsCredentialsTestProfile.class);
    }
}
