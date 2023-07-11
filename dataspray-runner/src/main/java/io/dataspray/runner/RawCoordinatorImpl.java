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

package io.dataspray.runner;

import com.google.common.base.Strings;
import io.dataspray.stream.client.StreamApiImpl;
import io.dataspray.stream.ingest.client.ApiException;
import io.dataspray.stream.ingest.client.IngestApi;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RawCoordinatorImpl implements RawCoordinator {

    public static final String DATASPRAY_DEFAULT_STORE_NAME = "default";
    public static final String DATASPRAY_API_KEY_ENV = "dataspray_api_key";
    public static final String DATASPRAY_CUSTOMER_ID_ENV = "dataspray_customer_id";
    private static volatile RawCoordinatorImpl INSTANCE;

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
    public void send(byte[] data, StoreType storeType, String storeName, String streamName) {
        switch (storeType) {
            case DATASPRAY:
                sendToDataSpray(data, storeName, streamName);
                break;
            default:
                log.error("Store type not supported: {}", storeType);
                throw new RuntimeException("Store type not supported: " + storeType);
        }
    }

    private void sendToDataSpray(byte[] data, String storeName, String streamName) {
        String apiKey = System.getenv(DATASPRAY_API_KEY_ENV);
        if (Strings.isNullOrEmpty(apiKey)) {
            log.error("DataSpray API key not found using env var {}", DATASPRAY_API_KEY_ENV);
            throw new RuntimeException("DataSpray API key not found using env var: " + DATASPRAY_API_KEY_ENV);
        }
        if (DATASPRAY_DEFAULT_STORE_NAME.equals(storeName)) {
            storeName = System.getenv(DATASPRAY_CUSTOMER_ID_ENV);
            if (Strings.isNullOrEmpty(storeName)) {
                log.error("DataSpray Customer ID not found using env var {}", DATASPRAY_CUSTOMER_ID_ENV);
                throw new RuntimeException("DataSpray Customer ID not found using env var: " + DATASPRAY_CUSTOMER_ID_ENV);
            }
        }
        IngestApi ingestApi = new StreamApiImpl().ingest(apiKey);
        try {
            ingestApi.message(storeName, streamName, data);
        } catch (ApiException ex) {
            log.error("Failed to send message to DataSpray for customer {} stream {}", storeName, streamName);
            throw new RuntimeException("Failed to send message to DataSpray for customer " + storeName + " stream " + streamName, ex);
        }
    }
}
