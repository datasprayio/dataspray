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

    /**
     * S3 bucket prefix path format for batch files.
     *
     * IMPORTANT: If you change this format, update the parsing regex in:
     * dataspray-site-dashboard/src/deployment/S3FileBrowser.tsx parseS3Key()
     */
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
    software.amazon.awssdk.services.s3.S3Client s3Client;
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

    @Override
    public FilesListResult listFiles(String organizationName, String topicName, BatchRetention retention, String prefix, int maxResults, String nextToken) {
        log.info("Listing files for organization: {}, topic: {}, prefix: {}", organizationName, topicName, prefix);

        // Build S3 prefix for this topic
        String basePrefix = ETL_BUCKET_TARGET_PREFIX.apply(retention, organizationName, topicName)
                .replace("/year=!{timestamp:yyyy}", "")
                .replace("/month=!{timestamp:MM}", "")
                .replace("/day=!{timestamp:dd}", "")
                .replace("/hour=!{timestamp:HH}/", "");

        String fullPrefix = prefix != null && !prefix.isEmpty()
                ? basePrefix + "/" + prefix
                : basePrefix;

        log.debug("Listing S3 objects with prefix: s3://{}/{}", etlBucketName, fullPrefix);

        // Limit max results
        int limitedMaxResults = Math.min(Math.max(1, maxResults), 1000);

        software.amazon.awssdk.services.s3.model.ListObjectsV2Request.Builder requestBuilder =
                software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                        .bucket(etlBucketName)
                        .prefix(fullPrefix)
                        .maxKeys(limitedMaxResults);

        if (nextToken != null && !nextToken.isEmpty()) {
            requestBuilder.continuationToken(nextToken);
        }

        software.amazon.awssdk.services.s3.model.ListObjectsV2Response response =
                s3Client.listObjectsV2(requestBuilder.build());

        java.util.List<S3File> files = response.contents().stream()
                .map(s3Object -> new S3File(
                        s3Object.key(),
                        s3Object.size(),
                        s3Object.lastModified()
                ))
                .collect(java.util.stream.Collectors.toList());

        return new FilesListResult(
                files,
                response.nextContinuationToken()
        );
    }

    @Override
    public PresignedUrl getFileDownloadUrl(String organizationName, String topicName, BatchRetention retention, String key) {
        log.info("Generating presigned URL for organization: {}, topic: {}, key: {}", organizationName, topicName, key);

        // Validate the key belongs to this topic's prefix (security check)
        String basePrefix = ETL_BUCKET_TARGET_PREFIX.apply(retention, organizationName, topicName)
                .replace("/year=!{timestamp:yyyy}", "")
                .replace("/month=!{timestamp:MM}", "")
                .replace("/day=!{timestamp:dd}", "")
                .replace("/hour=!{timestamp:HH}/", "");

        if (!key.startsWith(basePrefix)) {
            throw new IllegalArgumentException("Invalid file key - does not belong to this topic");
        }

        // Generate presigned URL valid for 15 minutes
        java.time.Duration expiration = java.time.Duration.ofMinutes(15);
        java.time.Instant expiresAt = java.time.Instant.now().plus(expiration);

        software.amazon.awssdk.services.s3.presigner.S3Presigner presigner =
                software.amazon.awssdk.services.s3.presigner.S3Presigner.create();

        try {
            software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest =
                    software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                            .bucket(etlBucketName)
                            .key(key)
                            .build();

            software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                    software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                            .signatureDuration(expiration)
                            .getObjectRequest(getObjectRequest)
                            .build();

            software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest presignedRequest =
                    presigner.presignGetObject(presignRequest);

            String url = presignedRequest.url().toString();
            log.debug("Generated presigned URL: {}", url);

            return new PresignedUrl(url, expiresAt);
        } finally {
            presigner.close();
        }
    }

    @Override
    public TableDefinition recalculateTableDefinition(String organizationName, String topicName, BatchRetention retention) {
        log.info("Recalculating schema for organization: {}, topic: {}", organizationName, topicName);

        // Build S3 prefix for this topic
        String s3Prefix = ETL_BUCKET_TARGET_PREFIX.apply(retention, organizationName, topicName)
                .replace("/year=!{timestamp:yyyy}", "")
                .replace("/month=!{timestamp:MM}", "")
                .replace("/day=!{timestamp:dd}", "")
                .replace("/hour=!{timestamp:HH}/", "");

        log.debug("Listing S3 objects with prefix: s3://{}/{}", etlBucketName, s3Prefix);

        // List objects to find sample data
        software.amazon.awssdk.services.s3.model.ListObjectsV2Request listRequest =
                software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                        .bucket(etlBucketName)
                        .prefix(s3Prefix)
                        .maxKeys(100)
                        .build();

        software.amazon.awssdk.services.s3.model.ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        if (listResponse.contents().isEmpty()) {
            throw new IllegalArgumentException("No data found for organization: " + organizationName +
                                               ", topic: " + topicName + ". Please ingest data first.");
        }

        // Read up to 10 sample objects to infer schema
        java.util.Set<String> allFields = new java.util.LinkedHashSet<>();
        int samplesRead = 0;
        int maxSamples = Math.min(10, listResponse.contents().size());

        for (software.amazon.awssdk.services.s3.model.S3Object s3Object : listResponse.contents()) {
            if (samplesRead >= maxSamples) break;
            if (s3Object.size() == 0) continue;

            try {
                // Get object
                software.amazon.awssdk.services.s3.model.GetObjectRequest getRequest =
                        software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                                .bucket(etlBucketName)
                                .key(s3Object.key())
                                .build();

                byte[] objectBytes = s3Client.getObjectAsBytes(getRequest).asByteArray();

                // Parse JSON (assuming JSON Lines format with potential GZIP)
                String content = new String(objectBytes, java.nio.charset.StandardCharsets.UTF_8);

                // Handle both single JSON object and JSON Lines format
                String[] lines = content.split("\\r?\\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        com.google.gson.JsonObject jsonObject = new com.google.gson.JsonParser()
                                .parse(line)
                                .getAsJsonObject();

                        // Collect all field names
                        jsonObject.keySet().forEach(allFields::add);
                    } catch (Exception e) {
                        log.warn("Failed to parse JSON line from {}: {}", s3Object.key(), e.getMessage());
                    }
                }

                samplesRead++;
            } catch (Exception e) {
                log.warn("Failed to read S3 object {}: {}", s3Object.key(), e.getMessage());
            }
        }

        if (allFields.isEmpty()) {
            throw new IllegalArgumentException("Could not parse any JSON data from S3 for topic: " + topicName);
        }

        log.info("Inferred {} fields from {} sample files", allFields.size(), samplesRead);

        // Build JSON schema
        com.google.gson.JsonObject schemaObject = new com.google.gson.JsonObject();
        schemaObject.addProperty("type", "object");
        com.google.gson.JsonObject properties = new com.google.gson.JsonObject();

        for (String field : allFields) {
            com.google.gson.JsonObject fieldSchema = new com.google.gson.JsonObject();
            // Default to string type - Athena will handle type coercion
            fieldSchema.addProperty("type", "string");
            properties.add(field, fieldSchema);
        }

        schemaObject.add("properties", properties);

        String schemaDefinition = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(schemaObject);

        log.debug("Inferred schema: {}", schemaDefinition);

        // Update the table with inferred schema
        setTableDefinition(organizationName, topicName, DataFormat.JSON, schemaDefinition, retention);

        return new TableDefinition(schemaDefinition, DataFormat.JSON);
    }

    /**
     * Registry is shared across customers. (AWS Limit is only 100 per region)
     */
    public static String getRegistryName(DeployEnvironment deployEnv) {
        return DeployEnvironment.RESOURCE_PREFIX + deployEnv.getSuffix().substring(1 /* Remove duplicate dash */) + "-customer";
    }
}
