/*
 * Copyright 2024 Matus Faro
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

package io.dataspray.store.impl;

import io.dataspray.common.DeployEnvironment;
import io.dataspray.store.BatchStore;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.OrganizationStore;
import io.dataspray.store.TopicStore.BatchRetention;
import io.dataspray.store.util.WaiterUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.function.TriFunction;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordRequest;
import software.amazon.awssdk.services.firehose.model.Record;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.Compatibility;
import software.amazon.awssdk.services.glue.model.CreateDatabaseRequest;
import software.amazon.awssdk.services.glue.model.CreateRegistryRequest;
import software.amazon.awssdk.services.glue.model.CreateSchemaRequest;
import software.amazon.awssdk.services.glue.model.CreateTableRequest;
import software.amazon.awssdk.services.glue.model.DataFormat;
import software.amazon.awssdk.services.glue.model.DatabaseInput;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.glue.model.GetRegistryRequest;
import software.amazon.awssdk.services.glue.model.GetRegistryResponse;
import software.amazon.awssdk.services.glue.model.GetSchemaRequest;
import software.amazon.awssdk.services.glue.model.GetSchemaResponse;
import software.amazon.awssdk.services.glue.model.GetSchemaVersionRequest;
import software.amazon.awssdk.services.glue.model.GetSchemaVersionResponse;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.RegisterSchemaVersionRequest;
import software.amazon.awssdk.services.glue.model.RegistryId;
import software.amazon.awssdk.services.glue.model.RegistryStatus;
import software.amazon.awssdk.services.glue.model.SchemaId;
import software.amazon.awssdk.services.glue.model.SchemaReference;
import software.amazon.awssdk.services.glue.model.SchemaVersionNumber;
import software.amazon.awssdk.services.glue.model.SchemaVersionStatus;
import software.amazon.awssdk.services.glue.model.SerDeInfo;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;
import software.amazon.awssdk.services.glue.model.UpdateTableRequest;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.dataspray.common.DeployEnvironment.DEPLOY_ENVIRONMENT_PROP_NAME;

@Slf4j
@ApplicationScoped
public class FirehoseS3AthenaBatchStore implements BatchStore {
    public static final String ETL_BUCKET_PROP_NAME = "etl.bucket.name";
    public static final String FIREHOSE_STREAM_NAME_PROP_NAME = "etl.firehose.name";
    public static final String GLUE_CUSTOMER_PREFIX = "customer-";
    public static final Function<DeployEnvironment, String> GLUE_CUSTOMER_PREFIX_GETTER = deployEnv ->
            DeployEnvironment.RESOURCE_PREFIX + deployEnv.getSuffix().substring(1 /* Remove duplicate dash */) + "-customer-";
    public static final String ETL_MESSAGE_TS = "_ds_message_ts";
    public static final String ETL_MESSAGE_ID = "_ds_message_id";
    public static final String ETL_MESSAGE_KEY = "_ds_message_key";
    public static final String ETL_PARTITION_KEY_RETENTION = "_ds_retention";
    public static final String ETL_PARTITION_KEY_ORGANIZATION = "_ds_organization";
    public static final String ETL_PARTITION_KEY_TOPIC = "_ds_topic";
    public static final String ETL_BUCKET_RETENTION_PREFIX_PREFIX = "retention=";
    public static final String ETL_BUCKET_RETENTION_PREFIX = ETL_BUCKET_RETENTION_PREFIX_PREFIX + "!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_RETENTION + "}";
    public static final String ETL_BUCKET_ERROR_PREFIX = ETL_BUCKET_RETENTION_PREFIX_PREFIX + BatchRetention.DAY.name() +
                                                         "/error" +
                                                         "/result=!{firehose:error-output-type}" +
                                                         "/year=!{timestamp:yyyy}" +
                                                         "/month=!{timestamp:MM}" +
                                                         "/day=!{timestamp:dd}" +
                                                         "/hour=!{timestamp:HH}" +
                                                         "/";
    public static final String ETL_BUCKET_ORGANIZATION_PREFIX = ETL_BUCKET_RETENTION_PREFIX +
                                                                "/organization=!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ORGANIZATION + "}";
    public static final BatchRetention ATHENA_RESULTS_DEFAULT_RETENTION = BatchRetention.WEEK;
    public static final String ETL_BUCKET_ATHENA_RESULTS_PREFIX = (ETL_BUCKET_ORGANIZATION_PREFIX +
                                                                   "/organization=!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ORGANIZATION + "}"
                                                                   + "/athena-results")
            .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_RETENTION + "}", ATHENA_RESULTS_DEFAULT_RETENTION.name());
    public static final String ETL_BUCKET_PREFIX = ETL_BUCKET_ORGANIZATION_PREFIX +
                                                   "/topic=!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_TOPIC + "}" +
                                                   "/year=!{timestamp:yyyy}" +
                                                   "/month=!{timestamp:MM}" +
                                                   "/day=!{timestamp:dd}" +
                                                   "/hour=!{timestamp:HH}" +
                                                   "/";
    public static final TriFunction<BatchRetention, String, String, String> ETL_BUCKET_TARGET_PREFIX = (etlBatchRetention, organizationName, topicName) -> ETL_BUCKET_PREFIX
            .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_RETENTION + "}", etlBatchRetention.name())
            .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ORGANIZATION + "}", organizationName)
            .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_TOPIC + "}", topicName);

    @ConfigProperty(name = DEPLOY_ENVIRONMENT_PROP_NAME)
    DeployEnvironment deployEnv;
    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = ETL_BUCKET_PROP_NAME)
    String etlBucketName;
    @ConfigProperty(name = FIREHOSE_STREAM_NAME_PROP_NAME)
    String firehoseStreamName;

    @Inject
    FirehoseClient firehoseClient;
    @Inject
    GlueClient glueClient;
    @Inject
    AthenaClient athenaClient;
    @Inject
    OrganizationStore organizationStore;
    @Inject
    CustomerLogger customerLog;
    @Inject
    WaiterUtil waiterUtil;

    @Override
    public String putRecord(byte[] messageBytes) {
        return firehoseClient.putRecord(PutRecordRequest.builder()
                        .deliveryStreamName(firehoseStreamName)
                        .record(Record.builder()
                                .data(SdkBytes.fromByteArrayUnsafe(messageBytes)).build()).build())
                .recordId();
    }

    @Override
    public Optional<TableDefinition> getTableDefinition(String organizationName, String topicName) {
        return getRegistry()
                .flatMap(registry -> getLatestSchemaVersion(organizationName, topicName, registry))
                .map(schemaVersion -> new TableDefinition(
                        schemaVersion.schemaDefinition(),
                        schemaVersion.dataFormat()));
    }

    @Override
    public void setTableDefinition(
            String organizationName,
            String topicName,
            DataFormat dataFormat,
            String schemaDefinition,
            BatchRetention retention) {

        GetRegistryResponse registry = getOrCreateRegistry();

        String schemaVersionId = createOrUpdateSchema(organizationName, topicName, dataFormat, schemaDefinition, registry);

        String databaseName = getOrCreateDatabase(organizationName);

        upsertTableAndSchema(organizationName, topicName, registry.registryName(), databaseName, schemaVersionId, retention);

        organizationStore.addGlueDatabaseToOrganization(organizationName, databaseName);
    }

    public static String getSchemaNameForQueue(String organizationName, String topicName) {
        return "customer-" + organizationName + "-topic-" + topicName;
    }

    private Optional<GetSchemaResponse> getSchema(String organizationName, String topicName, GetRegistryResponse registry) {
        String schemaName = getSchemaNameForQueue(organizationName, topicName);
        try {
            return Optional.of(glueClient.getSchema(GetSchemaRequest.builder()
                    .schemaId(SchemaId.builder()
                            .registryName(registry.registryName())
                            .schemaName(schemaName).build()).build()));
        } catch (EntityNotFoundException ex) {
            return Optional.empty();
        }
    }

    private String createOrUpdateSchema(String organizationName, String topicName, DataFormat dataFormat, String schemaDefinition, GetRegistryResponse registry) {
        String schemaName = getSchemaNameForQueue(organizationName, topicName);
        Optional<GetSchemaResponse> schemaPreviousOpt = getSchema(organizationName, topicName, registry);

        final String schemaVersionId;
        if (schemaPreviousOpt.isEmpty()) {
            schemaVersionId = glueClient.createSchema(CreateSchemaRequest.builder()
                            .registryId(RegistryId.builder()
                                    .registryName(registry.registryName()).build())
                            .schemaName(schemaName)
                            .compatibility(Compatibility.BACKWARD)
                            .dataFormat(dataFormat)
                            .schemaDefinition(schemaDefinition)
                            .build())
                    .schemaVersionId();
        } else {
            if (!dataFormat.equals(schemaPreviousOpt.get().dataFormat())) {
                throw new BadRequestException("Target " + topicName + " format is " + schemaPreviousOpt.get().dataFormat() + ", cannot changge format to " + dataFormat);
            }
            schemaVersionId = glueClient.registerSchemaVersion(RegisterSchemaVersionRequest.builder()
                            .schemaId(SchemaId.builder()
                                    .registryName(registry.registryName())
                                    .schemaName(schemaName).build())
                            .schemaDefinition(schemaDefinition)
                            .build())
                    .schemaVersionId();
        }

        GetSchemaVersionResponse response = waiterUtil.resolve(waiterUtil.waitUntilGlueSchemaCompletedState(schemaVersionId));
        if (!SchemaVersionStatus.AVAILABLE.equals(response.status())) {
            throw new BadRequestException("AWS Glue Schema registration resulted in " + response.statusAsString() + " status, likely the schema is not valid for AWS Glue.");
        }

        return schemaVersionId;
    }

    private Optional<GetSchemaVersionResponse> getLatestSchemaVersion(String organizationName, String topicName, GetRegistryResponse registry) {
        String schemaName = getSchemaNameForQueue(organizationName, topicName);
        try {
            return Optional.of(glueClient.getSchemaVersion(GetSchemaVersionRequest.builder()
                    .schemaId(SchemaId.builder()
                            .registryName(registry.registryName())
                            .schemaName(schemaName).build())
                    .schemaVersionNumber(SchemaVersionNumber.builder()
                            .latestVersion(true).build())
                    .build()));
        } catch (EntityNotFoundException ex) {
            return Optional.empty();
        }
    }

    private String getOrCreateDatabase(String organizationName) {
        String databaseName = getDatabaseName(deployEnv, organizationName);
        try {
            return glueClient.getDatabase(GetDatabaseRequest.builder()
                            .catalogId(awsAccountId)
                            .name(databaseName).build())
                    .database()
                    .name();
        } catch (EntityNotFoundException ex) {
            glueClient.createDatabase(CreateDatabaseRequest.builder()
                    .catalogId(awsAccountId)
                    .databaseInput(DatabaseInput.builder()
                            .name(databaseName).build()).build());
            return databaseName;
        }
    }

    public static String getDatabaseName(DeployEnvironment deployEnv, String organizationName) {
        return GLUE_CUSTOMER_PREFIX_GETTER.apply(deployEnv) + organizationName;
    }

    private void upsertTableAndSchema(
            String organizationName,
            String topicName,
            String registryName,
            String databaseName,
            String schemaVersionId,
            BatchRetention retention) {

        String schemaName = getSchemaNameForQueue(organizationName, topicName);
        String tableName = getTableName(topicName);
        Optional<Table> tablePreviousOpt;
        try {
            tablePreviousOpt = Optional.of(glueClient.getTable(GetTableRequest.builder()
                            .catalogId(awsAccountId)
                            .databaseName(databaseName)
                            .name(tableName).build())
                    .table());
        } catch (EntityNotFoundException ex) {
            tablePreviousOpt = Optional.empty();
        }

        if (tablePreviousOpt.isEmpty()) {
            glueClient.createTable(CreateTableRequest.builder()
                    .catalogId(awsAccountId)
                    .databaseName(databaseName)
                    .tableInput(TableInput.builder()
                            .name(tableName)
                            .description("Auto-created for customer " + organizationName + " topic " + topicName)
                            .tableType("EXTERNAL_TABLE")
                            .parameters(Map.of(
                                    "classification", "json",
                                    "compressionType", "gzip"))
                            .storageDescriptor(StorageDescriptor.builder()
                                    .location("s3://" + etlBucketName + "/" + ETL_BUCKET_TARGET_PREFIX.apply(
                                            retention,
                                            organizationName,
                                            topicName))
                                    .compressed(true)
                                    .inputFormat("org.apache.hadoop.mapred.TextInputFormat")
                                    .outputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat")
                                    .serdeInfo(SerDeInfo.builder()
                                            .serializationLibrary("org.openx.data.jsonserde.JsonSerDe")
                                            .parameters(Map.of("ignore.malformed.json", "true"))
                                            .build())
                                    .schemaReference(SchemaReference.builder()
                                            .schemaVersionId(schemaVersionId)
                                            .build())
                                    .build())
                            .build())
                    .build());
        } else if (schemaVersionId.equals(tablePreviousOpt.get()
                .storageDescriptor()
                .schemaReference()
                .schemaVersionId())) {
            // Nothing to do, changing schema version to version that already is set
            // Usually happens when updating schema with identical schema content to current version
        } else {
            glueClient.updateTable(UpdateTableRequest.builder()
                    .tableInput(TableInput.builder()
                            .storageDescriptor(StorageDescriptor.builder()
                                    .schemaReference(SchemaReference.builder()
                                            .schemaId(SchemaId.builder()
                                                    .registryName(registryName)
                                                    .schemaName(schemaName).build())
                                            .schemaVersionId(schemaVersionId).build()).build()).build()).build());
        }
    }

    public static String getTableName(String topicName) {
        return "stream-" + topicName;
    }

    private Optional<GetRegistryResponse> getRegistry() {
        try {
            return Optional.of(glueClient.getRegistry(GetRegistryRequest.builder()
                    .registryId(RegistryId.builder()
                            .registryName(getRegistryName(deployEnv)).build()).build()));
        } catch (EntityNotFoundException ex) {
            return Optional.empty();
        }
    }

    private GetRegistryResponse getOrCreateRegistry() {
        GetRegistryResponse response = getRegistry()
                .or(() -> {
                    glueClient.createRegistry(CreateRegistryRequest.builder()
                            .registryName(getRegistryName(deployEnv)).build());
                    return getRegistry();
                })
                .orElseThrow();
        if (RegistryStatus.DELETING.equals(response.status())) {
            throw new ConflictException("Another operation is in progress: "
                                        + "registry is in state " + response.statusAsString());
        }
        return response;
    }

    /**
     * Registry is shared across customers. (AWS Limit is only 100 per region)
     */
    public static String getRegistryName(DeployEnvironment deployEnv) {
        return DeployEnvironment.RESOURCE_PREFIX + deployEnv.getSuffix().substring(1 /* Remove duplicate dash */) + "-customer";
    }
}
