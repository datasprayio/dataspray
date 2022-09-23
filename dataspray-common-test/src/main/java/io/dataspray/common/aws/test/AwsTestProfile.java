package io.dataspray.common.aws.test;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AwsTestProfile extends CombinedTestProfile {

    public AwsTestProfile() {
        super(MockDynamoDbClient.TestProfile.class,
                MockS3Client.TestProfile.class,
                MockLambdaClient.TestProfile.class,
                TestAwsCredentialsTestProfile.class);
    }
}
