package io.dataspray.stream.ingest.deploy;

import com.google.common.collect.ImmutableList;
import io.dataspray.lambda.deploy.LambdaBaseStack;
import io.dataspray.store.BillingStore;
import io.dataspray.store.FirehoseS3AthenaEtlStore;
import io.dataspray.stream.ingest.IngestResource;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Size;
import software.amazon.awscdk.services.kinesisfirehose.CfnDeliveryStream;
import software.amazon.awscdk.services.kinesisfirehose.alpha.DeliveryStream;
import software.amazon.awscdk.services.kinesisfirehose.destinations.alpha.Compression;
import software.amazon.awscdk.services.kinesisfirehose.destinations.alpha.S3Bucket;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@Slf4j
public class IngestStack extends LambdaBaseStack {

    private final Bucket bucketEtl;
    private final DeliveryStream firehose;

    public IngestStack(Construct parent) {
        super(parent, Options.builder()
                .openapiYamlPath("target/openapi/api-ingest.yaml")
                .build());

        bucketEtl = Bucket.Builder.create(this, "ingest-etl-bucket")
                .bucketName(IngestResource.ETL_BUCKET_NAME)
                .autoDeleteObjects(false)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                // Add different expiry for each retention prefix
                .lifecycleRules(Arrays.stream(BillingStore.EtlRetention.values()).map(retention -> LifecycleRule.builder()
                        .expiration(Duration.days(retention.getExpirationInDays()))
                        .prefix(FirehoseS3AthenaEtlStore.ETL_BUCKET_RETENTION_PREFIX
                                .replace("!{partitionKeyFromQuery:" + FirehoseS3AthenaEtlStore.ETL_PARTITION_KEY_RETENTION + "}", retention.name()))
                        .build()).collect(Collectors.toList()))
                .build();

        firehose = DeliveryStream.Builder.create(this, "ingest-etl-firehose")
                .deliveryStreamName(FirehoseS3AthenaEtlStore.FIREHOSE_STREAM_NAME)
                .destinations(ImmutableList.of(S3Bucket.Builder.create(bucketEtl)
                        .bufferingInterval(Duration.seconds(900))
                        .bufferingSize(Size.mebibytes(128))
                        .compression(Compression.ZIP)
                        .dataOutputPrefix(FirehoseS3AthenaEtlStore.ETL_BUCKET_PREFIX)
                        .build()))
                .build();
        // AWS CDK doesn't yet support some configuration properties; adding overrides here
        CfnDeliveryStream firehoseCfn = (CfnDeliveryStream) requireNonNull(firehose.getNode().getDefaultChild());
        // https://stackoverflow.com/questions/69038997/which-class-in-aws-cdk-have-option-to-configure-dynamic-partitioning-for-kinesis
        firehoseCfn.addPropertyOverride("ExtendedS3DestinationConfiguration.ProcessingConfiguration",
                CfnDeliveryStream.ProcessingConfigurationProperty.builder()
                        .enabled(true)
                        .processors(List.of(
                                CfnDeliveryStream.ProcessorProperty.builder()
                                        .type("Lambda")
                                        // the properties below are optional
                                        .parameters(Stream.of(
                                                        FirehoseS3AthenaEtlStore.ETL_PARTITION_KEY_RETENTION,
                                                        FirehoseS3AthenaEtlStore.ETL_PARTITION_KEY_ACCOUNT,
                                                        FirehoseS3AthenaEtlStore.ETL_PARTITION_KEY_TARGET)
                                                .map(key -> CfnDeliveryStream.ProcessorParameterProperty.builder()
                                                        .parameterName(key)
                                                        .parameterValue(/* JQ format */ "." + key).build())
                                                .collect(Collectors.toList()))
                                        .build()
                        )).build()
                        .$jsii$toJson());
        // https://github.com/aws/aws-cdk/issues/19413
        firehoseCfn.addPropertyOverride(
                "ExtendedS3DestinationConfiguration.DynamicPartitioningConfiguration",
                CfnDeliveryStream.DynamicPartitioningConfigurationProperty.builder()
                        .enabled(true)
                        .retryOptions(CfnDeliveryStream.RetryOptionsProperty.builder()
                                .durationInSeconds(7200).build()).build()
                        .$jsii$toJson());
    }

    public static void main(String[] args) {
        App app = new App();
        new IngestStack(app);
        app.synth();
    }
}
