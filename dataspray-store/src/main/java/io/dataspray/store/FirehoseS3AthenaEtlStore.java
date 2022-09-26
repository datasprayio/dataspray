package io.dataspray.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dataspray.store.BillingStore.EtlRetention;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordRequest;
import software.amazon.awssdk.services.firehose.model.Record;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class FirehoseS3AthenaEtlStore implements EtlStore {
    public static final String FIREHOSE_STREAM_NAME = "dataspray-ingest-etl";
    public static final String ETL_PARTITION_KEY_RETENTION = "_ds_retention";
    public static final String ETL_PARTITION_KEY_ACCOUNT = "_ds_account";
    public static final String ETL_PARTITION_KEY_TARGET = "_ds_target";
    public static final String ETL_BUCKET_RETENTION_PREFIX = "/retention=!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_RETENTION + "}";
    public static final String ETL_BUCKET_PREFIX = ETL_BUCKET_RETENTION_PREFIX +
            "/account=!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ACCOUNT + "}" +
            "/target=!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_TARGET + "}" +
            "/year=!{timestamp:yyyy}" +
            "/month=!{timestamp:MM}" +
            "/day=!{timestamp:dd}" +
            "/hour=!{timestamp:HH}" +
            "/";
    @Inject
    CustomerLogger customerLog;
    @Inject
    FirehoseClient firehoseClient;

    private final ObjectMapper jsonSerde = new ObjectMapper();

    @Override
    public void putRecord(String accountId, String targetId, byte[] jsonBytes, EtlRetention retention) {
        // Parse customer mesage as JSON
        final Map<String, Object> json;
        try {
            json = jsonSerde.readValue(jsonBytes, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            customerLog.warn("Failed to parse message as JSON Object for target " + targetId + ", skipping ETL", accountId);
            return;
        }

        // Add metadata for Firehose dynamic partitioning
        json.put(ETL_PARTITION_KEY_RETENTION, retention.name());
        json.put(ETL_PARTITION_KEY_ACCOUNT, accountId);
        json.put(ETL_PARTITION_KEY_TARGET, targetId);
        final byte[] jsonWithMetadataBytes;
        try {
            jsonWithMetadataBytes = jsonSerde.writeValueAsBytes(json);
        } catch (JsonProcessingException ex) {
            customerLog.warn("Failed to parse message as JSON for target " + targetId + ", skipping ETL", accountId);
            log.warn("Failed to write json with metadata", ex);
            return;
        }

        firehoseClient.putRecord(PutRecordRequest.builder()
                .deliveryStreamName(FIREHOSE_STREAM_NAME)
                .record(Record.builder()
                        .data(SdkBytes.fromByteArrayUnsafe(jsonWithMetadataBytes)).build()).build());
    }
}
