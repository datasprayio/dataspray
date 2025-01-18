/*
 * Copyright 2025 Matus Faro
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

import com.google.common.base.Charsets;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.dataspray.store.CustomerMessageSerde;
import io.dataspray.store.TopicStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static io.dataspray.store.impl.FirehoseS3AthenaBatchStore.*;

@Slf4j
@ApplicationScoped
public class GsonCustomerMessageSerde implements CustomerMessageSerde {

    @Inject
    Gson gson;

    @Override
    public String bytesToString(byte[] messageBytes, MediaType contentType) {
        return switch (contentType.toString()) {
            // Text based messages send as string
            case MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN -> new String(messageBytes, Charsets.UTF_8);
            // Binary messages send as base64
            case "application/octet-stream", "application/avro", "application/protobuf" ->
                    Base64.getEncoder().encodeToString(messageBytes);
            default -> throw new ClientErrorException(Response.Status.UNSUPPORTED_MEDIA_TYPE);
        };
    }

    @Override
    public Map<String, Object> stringToJson(String messageStr) {
        return gson.fromJson(messageStr, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    @Override
    public Map<String, Object> enrichJson(String organizationName, String topicName, Optional<TopicStore.BatchRetention> batchRetentionOpt, Optional<String> messageIdOpt, String messageKey, Map<String, Object> messageJson) {

        // Add extra attributes
        messageJson.put(ETL_MESSAGE_KEY, messageKey);
        messageIdOpt.ifPresent(messageId ->
                messageJson.put(ETL_MESSAGE_ID, messageId));

        // Add metadata for Firehose dynamic partitioning
        batchRetentionOpt.ifPresent(batchRetention ->
                messageJson.put(ETL_PARTITION_KEY_RETENTION, batchRetention.name()));
        messageJson.put(ETL_PARTITION_KEY_ORGANIZATION, organizationName);
        messageJson.put(ETL_PARTITION_KEY_TOPIC, topicName);

        return messageJson;
    }

    @Override
    @SneakyThrows
    public byte[] jsonToBytes(Map<String, Object> messageJson) {
        // Write to bytes without intermediate step of converting to String
        // using gson.toJson(messageJson).getBytes(Charsets.UTF_8);
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(byteArrayOutputStream, Charsets.UTF_8)) {
            gson.toJson(messageJson, writer);
            writer.flush();
            return byteArrayOutputStream.toByteArray();
        }
    }
}
