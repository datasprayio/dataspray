package io.dataspray.stream.control.deploy;

import io.dataspray.common.aws.test.MockS3Client;
import io.findify.s3mock.S3Mock;
import io.findify.s3mock.request.CreateBucketConfiguration;
import scala.Option;

import static io.dataspray.store.LambdaDeployerImpl.CODE_BUCKET_NAME;

public class MockControlStack {

    public static void mock(S3Mock s3Mock) {
        s3Mock.p().createBucket(
                CODE_BUCKET_NAME,
                new CreateBucketConfiguration(Option.apply(MockS3Client.REGION.id())));
    }
}
