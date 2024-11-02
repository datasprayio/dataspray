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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dataspray.store.BatchStore;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.TopicStore.BatchRetention;
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
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.DatabaseInput;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.glue.model.GetRegistryRequest;
import software.amazon.awssdk.services.glue.model.GetRegistryResponse;
import software.amazon.awssdk.services.glue.model.GetSchemaRequest;
import software.amazon.awssdk.services.glue.model.GetSchemaResponse;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.RegisterSchemaVersionRequest;
import software.amazon.awssdk.services.glue.model.RegistryId;
import software.amazon.awssdk.services.glue.model.RegistryStatus;
import software.amazon.awssdk.services.glue.model.SchemaId;
import software.amazon.awssdk.services.glue.model.SchemaReference;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;
import software.amazon.awssdk.services.glue.model.UpdateTableRequest;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class FirehoseS3AthenaBatchStore implements BatchStore {
    public static final String ETL_BUCKET_PROP_NAME = "etl.bucket.name";
    public static final String FIREHOSE_STREAM_NAME_PROP_NAME = "etl.firehose.name";
    public static final String GLUE_CUSTOMER_PREFIX = "customer-";
    public static final String GLUE_SCHEMA_FOR_QUEUE_PREFIX = "queue-";
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
    public static final String ETL_BUCKET_PREFIX = ETL_BUCKET_RETENTION_PREFIX +
                                                   "/organization=!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ORGANIZATION + "}" +
                                                   "/topic=!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_TOPIC + "}" +
                                                   "/year=!{timestamp:yyyy}" +
                                                   "/month=!{timestamp:MM}" +
                                                   "/day=!{timestamp:dd}" +
                                                   "/hour=!{timestamp:HH}" +
                                                   "/";
    public static final TriFunction<BatchRetention, String, String, String> ETL_BUCKET_TARGET_PREFIX = (etlBatchRetention, customerId, topicName) -> ETL_BUCKET_PREFIX
            .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_RETENTION + "}", etlBatchRetention.name())
            .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ORGANIZATION + "}", customerId)
            .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_TOPIC + "}", topicName);

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
    CustomerLogger customerLog;

    private final ObjectMapper jsonSerde = new ObjectMapper();

    @Override
    public void putRecord(String customerId, String topicName, Optional<String> messageIdOpt, String messageKey, byte[] jsonBytes, BatchRetention retention) {
        // Parse customer message as JSON
        // TODO Optimization: parse only beginning, look for '{', inject metadata, and copy the rest; fallback to this if parsing fails
        final Map<String, Object> json;
        try {
            json = jsonSerde.readValue(jsonBytes, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            customerLog.warn("Failed to parse message as JSON Object for topic " + topicName + ", skipping ETL", customerId);
            return;
        }

        // Add extra attributes
        json.put(ETL_MESSAGE_KEY, messageKey);
        json.put(ETL_MESSAGE_ID, messageIdOpt.orElse(null));

        // Add metadata for Firehose dynamic partitioning
        json.put(ETL_PARTITION_KEY_RETENTION, retention.name());
        json.put(ETL_PARTITION_KEY_ORGANIZATION, customerId);
        json.put(ETL_PARTITION_KEY_TOPIC, topicName);
        final byte[] jsonWithMetadataBytes;
        try {
            jsonWithMetadataBytes = jsonSerde.writeValueAsBytes(json);
        } catch (JsonProcessingException ex) {
            customerLog.warn("Failed to parse message as JSON for topic " + topicName + ", skipping ETL", customerId);
            log.warn("Failed to write json with metadata", ex);
            return;
        }

        firehoseClient.putRecord(PutRecordRequest.builder()
                .deliveryStreamName(firehoseStreamName)
                .record(Record.builder()
                        .data(SdkBytes.fromByteArrayUnsafe(jsonWithMetadataBytes)).build()).build());
    }

    @Override
    public void setTableDefinition(
            String customerId,
            String topicName,
            DataFormat dataFormat,
            String schemaDefinition,
            BatchRetention retention) {
        GetRegistryResponse registry = getOrCreateRegistry(customerId);

        String schemaName = getSchemaNameForQueue(topicName);
        Optional<GetSchemaResponse> schemaPreviousOpt;
        try {
            schemaPreviousOpt = Optional.of(glueClient.getSchema(GetSchemaRequest.builder()
                    .schemaId(SchemaId.builder()
                            .registryName(registry.registryName())
                            .schemaName(schemaName).build()).build()));
        } catch (EntityNotFoundException ex) {
            schemaPreviousOpt = Optional.empty();
        }

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

        Database database = getOrCreateDatabase(customerId);

        upsertTableAndSchema(customerId, topicName, registry.registryName(), schemaName, schemaVersionId, retention);
    }

    private String getSchemaNameForQueue(String topicName) {
        return GLUE_SCHEMA_FOR_QUEUE_PREFIX + topicName;
    }

    private Database getOrCreateDatabase(String customerId) {
        try {
            return glueClient.getDatabase(GetDatabaseRequest.builder()
                            .catalogId(awsAccountId)
                            .name(getDatabaseName(customerId)).build())
                    .database();
        } catch (EntityNotFoundException ex) {
            glueClient.createDatabase(CreateDatabaseRequest.builder()
                    .catalogId(awsAccountId)
                    .databaseInput(DatabaseInput.builder()
                            .name(getDatabaseName(customerId)).build()).build());
            return glueClient.getDatabase(GetDatabaseRequest.builder()
                            .catalogId(awsAccountId)
                            .name(getDatabaseName(customerId)).build())
                    .database();
        }
    }

    private String getDatabaseName(String customerId) {
        return GLUE_CUSTOMER_PREFIX + customerId;
    }

    private void upsertTableAndSchema(
            String customerId,
            String topicName,
            String registryName,
            String schemaName,
            String schemaVersionId,
            BatchRetention retention) {
        String tableName = getTableName(customerId, topicName);
        Optional<Table> tablePreviousOpt;
        try {
            tablePreviousOpt = Optional.of(glueClient.getTable(GetTableRequest.builder()
                            .catalogId(awsAccountId)
                            .databaseName(getDatabaseName(customerId))
                            .name(tableName).build())
                    .table());
        } catch (EntityNotFoundException ex) {
            tablePreviousOpt = Optional.empty();
        }

        if (tablePreviousOpt.isEmpty()) {
            glueClient.createTable(CreateTableRequest.builder()
                    .catalogId(awsAccountId)
                    .databaseName(getDatabaseName(customerId))
                    .tableInput(TableInput.builder()
                            .name(tableName)
                            .storageDescriptor(StorageDescriptor.builder()
                                    .location("s3://" + etlBucketName + "/" + ETL_BUCKET_TARGET_PREFIX.apply(
                                            retention,
                                            customerId,
                                            topicName))
                                    .schemaReference(SchemaReference.builder()
                                            .schemaId(SchemaId.builder()
                                                    .registryName(registryName)
                                                    .schemaName(schemaName).build())
                                            .schemaVersionId(schemaVersionId).build()).build()).build())
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

    private String getTableName(String customerId, String topicName) {
        return GLUE_CUSTOMER_PREFIX + customerId + "-queue-" + topicName;
    }

    private GetRegistryResponse getOrCreateRegistry(String customerId) {
        GetRegistryResponse response;
        try {
            response = glueClient.getRegistry(GetRegistryRequest.builder()
                    .registryId(RegistryId.builder()
                            .registryName(getRegistryName(customerId)).build()).build());
        } catch (EntityNotFoundException ex) {
            glueClient.createRegistry(CreateRegistryRequest.builder()
                    .registryName(getRegistryName(customerId)).build());
            response = glueClient.getRegistry(GetRegistryRequest.builder()
                    .registryId(RegistryId.builder()
                            .registryName(getRegistryName(customerId)).build()).build());
        }
        if (RegistryStatus.DELETING.equals(response.status())) {
            throw new ConflictException("Another operation is in progress: "
                                        + "registry is in state " + response.statusAsString());
        }
        return response;
    }

    private String getRegistryName(String customerId) {
        return GLUE_CUSTOMER_PREFIX + customerId;
    }
}
