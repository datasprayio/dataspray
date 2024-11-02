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

package io.dataspray.runner;

import com.google.common.base.Strings;
import io.dataspray.client.Access;
import io.dataspray.client.DataSprayClient;
import io.dataspray.stream.ingest.client.ApiException;
import io.dataspray.stream.ingest.client.IngestApi;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;

@Slf4j
public class RawCoordinatorImpl implements RawCoordinator {

    /** Matches io.dataspray.store.LambdaDeployerImpl.DATASPRAY_API_KEY_ENV */
    public static final String DATASPRAY_API_KEY_ENV = "dataspray_api_key";
    /** Matches io.dataspray.store.LambdaDeployerImpl.DATASPRAY_ORGANIZATION_NAME_ENV */
    public static final String DATASPRAY_ORGANIZATION_NAME_ENV = "dataspray_organization_name";
    /** Matches io.dataspray.store.LambdaDeployerImpl.DATASPRAY_ENDPOINT_ENV */
    public static final String DATASPRAY_ENDPOINT_ENV = "dataspray_endpoint";
    private static volatile RawCoordinatorImpl INSTANCE;

    private volatile Optional<IngestApi> ingestApiOpt;

    private RawCoordinatorImpl() {
    }

    public static RawCoordinatorImpl get() {
        if (INSTANCE == null) {
            synchronized (RawCoordinatorImpl.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RawCoordinatorImpl();
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public void send(String messageKey, byte[] data, StoreType storeType, String storeName, String streamName, @Nullable String messageId) {
        switch (storeType) {
            case DATASPRAY:
                sendToDataSpray(messageKey, data, storeName, streamName, messageId);
                break;
            case KAFKA:
            default:
                log.error("Store type not supported: {}", storeType);
                throw new RuntimeException("Store type not supported: " + storeType);
        }
    }

    @Override
    public StateManager getStateManager(String[] key, @Nullable Duration ttl) {
        return StateManagerFactoryImpl.getOrCreate().getStateManager(key, Optional.ofNullable(ttl));
    }

    private void sendToDataSpray(String messageKey, byte[] data, String storeName, String streamName, @Nullable String messageId) {
        try {
            getIngestApi().message(storeName, streamName, messageKey, data, messageId);
        } catch (ApiException ex) {
            log.error("Failed to send message to DataSpray for customer {} stream {}", storeName, streamName);
            throw new RuntimeException("Failed to send message to DataSpray for customer " + storeName + " stream " + streamName, ex);
        }
    }

    private IngestApi getIngestApi() {
        if (ingestApiOpt.isEmpty()) {
            synchronized (this) {
                if (ingestApiOpt.isEmpty()) {
                    // Fetch api key
                    String apiKey = System.getenv(DATASPRAY_API_KEY_ENV);
                    if (Strings.isNullOrEmpty(apiKey)) {
                        log.error("DataSpray API key not found using env var {}", DATASPRAY_API_KEY_ENV);
                        throw new RuntimeException("DataSpray API key not found using env var: " + DATASPRAY_API_KEY_ENV);
                    }

                    // Fetch organization name
                    String organizationName = System.getenv(DATASPRAY_ORGANIZATION_NAME_ENV);
                    if (Strings.isNullOrEmpty(organizationName)) {
                        log.error("DataSpray organization name not found using env var {}", DATASPRAY_ORGANIZATION_NAME_ENV);
                        throw new RuntimeException("DataSpray organization name not found using env var: " + DATASPRAY_ORGANIZATION_NAME_ENV);
                    }

                    // Fetch endpoint
                    Optional<String> endpointOpt = Optional.ofNullable(Strings.emptyToNull(System.getenv(DATASPRAY_ENDPOINT_ENV)));

                    ingestApiOpt = Optional.of(DataSprayClient.get(new Access(
                                    apiKey,
                                    endpointOpt))
                            .ingest());
                }
            }
        }
        return ingestApiOpt.get();
    }
}
