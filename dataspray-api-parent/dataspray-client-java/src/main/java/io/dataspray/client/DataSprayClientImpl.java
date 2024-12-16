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

package io.dataspray.client;

import com.google.common.base.Strings;
import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.control.client.HealthApi;
import io.dataspray.stream.ingest.client.ApiCallback;
import io.dataspray.stream.ingest.client.ApiException;
import io.dataspray.stream.ingest.client.IngestApi;
import io.dataspray.stream.ingest.client.ProgressResponseBody;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
class DataSprayClientImpl implements DataSprayClient {

    private final Access access;

    public DataSprayClientImpl(Access access) {
        this.access = access;
    }

    @Override
    public HealthApi health() {
        io.dataspray.stream.control.client.ApiClient apiClient = new io.dataspray.stream.control.client.ApiClient();
        access.getEndpoint().ifPresent(apiClient::setBasePath);
        apiClient.setHttpClient(getHttpClient(false, false));
        apiClient.setApiKeyPrefix("apikey");
        apiClient.setApiKey(access.getApiKey());
        return new HealthApi(apiClient);
    }

    @Override
    public IngestApi ingest() {
        io.dataspray.stream.ingest.client.ApiClient apiClient = new io.dataspray.stream.ingest.client.ApiClient();
        access.getEndpoint().ifPresent(apiClient::setBasePath);
        apiClient.setHttpClient(getHttpClient(false, false));
        apiClient.setApiKeyPrefix("apikey");
        apiClient.setApiKey(access.getApiKey());
        return new IngestApi(apiClient);
    }

    @Override
    public ControlApi control() {
        io.dataspray.stream.control.client.ApiClient apiClient = new io.dataspray.stream.control.client.ApiClient();
        access.getEndpoint().ifPresent(apiClient::setBasePath);
        apiClient.setHttpClient(getHttpClient(false, false));
        apiClient.setApiKeyPrefix("apikey");
        apiClient.setApiKey(access.getApiKey());
        return new ControlApi(apiClient);
    }

    private OkHttpClient getHttpClient(boolean uploadProgressBar, boolean downloadProgressBar) {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMinutes(1))
                .readTimeout(Duration.ofMinutes(1))
                .writeTimeout(Duration.ofMinutes(1))
                .addNetworkInterceptor(chain -> {
                    Request request = chain.request();
                    log.trace("Method: {}", request.method());
                    log.trace("URL: {}", request.url());
                    request.headers().toMultimap().forEach((key, val) ->
                            log.trace("Request header: {} {}", key, val));
                    Response originalResponse = chain.proceed(request);
                    return originalResponse.newBuilder()
                            .body(new ProgressResponseBody(originalResponse.body(), new ApiCallback<>() {
                                private boolean firstUpdate = true;

                                @Override
                                public void onFailure(ApiException ex, int statusCode, Map<String, List<String>> responseHeaders) {
                                    log.trace("Failed response: {}", statusCode, ex);
                                    responseHeaders.forEach((key, val) ->
                                            log.trace("Response header: {} {}", key, val));
                                }

                                @Override
                                public void onSuccess(Object result, int statusCode, Map<String, List<String>> responseHeaders) {
                                    log.trace("Response: {}", statusCode);
                                    responseHeaders.forEach((key, val) ->
                                            log.trace("Response header: {} {}", key, val));
                                }

                                @Override
                                public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                                    if (uploadProgressBar) {
                                        onProgress(bytesWritten, contentLength, done, false);
                                    }
                                }

                                @Override
                                public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                                    if (downloadProgressBar) {
                                        onProgress(bytesRead, contentLength, done, true);
                                    }
                                }

                                private void onProgress(long bytes, long contentLength, boolean done, boolean isDownload) {
                                    if (done) {
                                        log.info("{} completed", isDownload ? "download" : "upload");
                                    } else {
                                        if (firstUpdate) {
                                            firstUpdate = false;
                                            if (contentLength == -1) {
                                                log.info("content-length: unknown");
                                            } else {
                                                log.info("content-length: {}", contentLength);
                                            }
                                        }

                                        if (contentLength != -1) {
                                            log.info("{} bytes {}", bytes, isDownload ? "read" : "written");
                                        } else {
                                            log.info("{} done", String.format("%d%%", (100 * bytes) / contentLength));
                                        }
                                    }
                                }
                            }))
                            .build();
                })
                .build();
    }

    /**
     * Code from example: <a
     * href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html">AWS
     * example</a>
     */
    @Override
    public void uploadCode(String presignedUrlStr, File file) throws IOException {
        log.trace("Uploading file {} to presigned url {}", file.getPath(), presignedUrlStr);
        try (Response response = getHttpClient(true, false)
                .newCall(new Request.Builder()
                        .url(presignedUrlStr)
                        .put(RequestBody.create(MediaType.get("application/zip"), file))
                        .build())
                .execute()) {
            if (response.code() < 200 || response.code() > 299) {
                throw new IOException("Failed with status " + response.code() + " to upload to S3: " + Strings.nullToEmpty(response.message()));
            }
        }
    }
}
