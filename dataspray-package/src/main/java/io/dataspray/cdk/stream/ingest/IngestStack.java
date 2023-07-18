/*
 * Copyright 2023 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.cdk.stream.ingest;

import com.google.common.collect.ImmutableList;
import io.dataspray.cdk.web.BaseLambdaWebServiceStack;
import io.dataspray.store.AccountStore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Size;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.kinesisfirehose.CfnDeliveryStream;
import software.amazon.awscdk.services.kinesisfirehose.alpha.DeliveryStream;
import software.amazon.awscdk.services.kinesisfirehose.destinations.alpha.Compression;
import software.amazon.awscdk.services.kinesisfirehose.destinations.alpha.S3Bucket;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IntelligentTieringConfiguration;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.dataspray.store.FirehoseS3AthenaEtlStore.*;
import static io.dataspray.store.SqsQueueStore.CUSTOMER_QUEUE_WILDCARD;
import static java.util.Objects.requireNonNull;

@Slf4j
public class IngestStack extends BaseLambdaWebServiceStack {

    @Getter
    private final String bucketEtlName;
    @Getter
    private final Bucket bucketEtl;
    @Getter
    private final String firehoseName;
    @Getter
    private final DeliveryStream firehose;

    public IngestStack(Construct parent, String env, String codeZip) {
        super(parent, Options.builder()
                .env(env)
                .functionName("ingest-" + env)
                .codeZip(codeZip)
                .build());

        getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid("CustomerIngestSqs")
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "sqs:SendMessage",
                        "sqs:CreateQueue"))
                .resources(ImmutableList.of(
                        "arn:aws:sqs:" + getRegion() + ":" + getAccount() + ":" + CUSTOMER_QUEUE_WILDCARD))
                .build());

        bucketEtlName = getSubConstructId("ingest-etl-bucket");
        bucketEtl = Bucket.Builder.create(this, getSubConstructId("ingest-etl-bucket"))
                .bucketName(bucketEtlName)
                .autoDeleteObjects(false)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                // Add different expiry for each retention prefix
                .lifecycleRules(Arrays.stream(AccountStore.EtlRetention.values()).map(retention -> LifecycleRule.builder()
                        .id(retention.name())
                        .expiration(Duration.days(retention.getExpirationInDays()))
                        .prefix(ETL_BUCKET_RETENTION_PREFIX + retention.name())
                        .build()).collect(Collectors.toList()))
                // Move objects to archive after inactivity to save costs
                .intelligentTieringConfigurations(Arrays.stream(AccountStore.EtlRetention.values())
                        // Only makes sense for data stored for more than 4 months (migrated after 3)
                        .filter(retention -> retention.getExpirationInDays() > 120)
                        .map(retention -> IntelligentTieringConfiguration.builder()
                                .name(retention.name())
                                .prefix(ETL_BUCKET_RETENTION_PREFIX_PREFIX + retention.name())
                                .archiveAccessTierTime(Duration.days(90))
                                .deepArchiveAccessTierTime(Duration.days(180))
                                .build()).collect(Collectors.toList()))
                .build();

        firehoseName = getSubConstructId("ingest-etl-firehose");
        firehose = DeliveryStream.Builder.create(this, getSubConstructId("ingest-etl-firehose"))
                .deliveryStreamName(firehoseName)
                .destinations(ImmutableList.of(S3Bucket.Builder.create(bucketEtl)
                        .bufferingInterval(Duration.seconds(900))
                        .bufferingSize(Size.mebibytes(128))
                        .compression(Compression.ZIP)
                        .dataOutputPrefix(ETL_BUCKET_PREFIX)
                        .errorOutputPrefix(ETL_BUCKET_ERROR_PREFIX)
                        .logging(true)
                        .build()))
                .build();
        // AWS CDK doesn't yet support some configuration properties; adding overrides here
        CfnDeliveryStream firehoseCfn = (CfnDeliveryStream) requireNonNull(firehose.getNode().getDefaultChild());
        // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-kinesisfirehose-deliverystream-processingconfiguration.html
        firehoseCfn.addPropertyOverride("ExtendedS3DestinationConfiguration.ProcessingConfiguration",
                Map.of("Enabled", Boolean.TRUE, "Processors", List.of(
                        Map.of("Type", "AppendDelimiterToRecord", "Parameters", List.of(
                                Map.of("ParameterName", "Delimiter",
                                        "ParameterValue", "\\n"))),
                        Map.of("Type", "MetadataExtraction", "Parameters", List.of(
                                Map.of("ParameterName", "JsonParsingEngine",
                                        "ParameterValue", "JQ-1.6"),
                                Map.of("ParameterName", "MetadataExtractionQuery",
                                        "ParameterValue", "{" +
                                                          Stream.of(ETL_PARTITION_KEY_RETENTION, ETL_PARTITION_KEY_ACCOUNT, ETL_PARTITION_KEY_TARGET)
                                                                  .map(key -> key + ":." + key)
                                                                  .collect(Collectors.joining(","))
                                                          + "}"))))));
        // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-kinesisfirehose-deliverystream-dynamicpartitioningconfiguration.html
        firehoseCfn.addPropertyOverride(
                "ExtendedS3DestinationConfiguration.DynamicPartitioningConfiguration",
                Map.of("Enabled", Boolean.TRUE, "RetryOptions", Map.of(
                        "DurationInSeconds", 300L)));
        getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "firehose:PutRecord"))
                .resources(ImmutableList.of(
                        firehose.getDeliveryStreamArn()))
                .build());
    }
}
