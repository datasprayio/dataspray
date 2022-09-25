package io.dataspray.stream.ingest.deploy;

import com.google.common.collect.ImmutableList;
import io.dataspray.lambda.deploy.LambdaBaseStack;
import io.dataspray.stream.ingest.IngestResource;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Size;
import software.amazon.awscdk.services.kinesisfirehose.alpha.DeliveryStream;
import software.amazon.awscdk.services.kinesisfirehose.destinations.alpha.Compression;
import software.amazon.awscdk.services.kinesisfirehose.destinations.alpha.S3Bucket;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;

@Slf4j
public class IngestStack extends LambdaBaseStack {

    private final Bucket bucketEtl;

    public IngestStack(Construct parent) {
        super(parent, Options.builder()
                .openapiYamlPath("target/openapi/api-ingest.yaml")
                .build());

        bucketEtl = Bucket.Builder.create(this, "ingest-etl-bucket")
                .bucketName(IngestResource.ETL_BUCKET_NAME)
                .autoDeleteObjects(false)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .lifecycleRules(ImmutableList.of(
                        LifecycleRule.builder()
                                .expiration(Duration.days(30)).build()))
                .build();

        DeliveryStream.Builder.create(this, "ingest-etl-firehose")
                .deliveryStreamName("dataspray-ingest-etl")
                .destinations(ImmutableList.of(
                        S3Bucket.Builder.create(bucketEtl)
                                .bufferingInterval(Duration.seconds(900))
                                .bufferingSize(Size.mebibytes(128))
                                .compression(Compression.ZIP)
                                .dataOutputPrefix(IngestResource.ETL_BUCKET_PREFIX)
                                .build()
                ))
                .build();
    }

    public static void main(String[] args) {
        App app = new App();
        new IngestStack(app);
        app.synth();
    }
}
