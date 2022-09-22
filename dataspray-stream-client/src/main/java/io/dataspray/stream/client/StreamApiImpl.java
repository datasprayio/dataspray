package io.dataspray.stream.client;

import com.google.common.base.Strings;
import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.ingest.client.IngestApi;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@ApplicationScoped
public class StreamApiImpl implements StreamApi {
    @Override
    public IngestApi ingest(String apiKey) {
        io.dataspray.stream.ingest.client.ApiClient apiClient = new io.dataspray.stream.ingest.client.ApiClient();
        apiClient.setApiKey(apiKey);
        return new IngestApi(apiClient);
    }

    @Override
    public ControlApi control(String apiKey) {
        io.dataspray.stream.control.client.ApiClient apiClient = new io.dataspray.stream.control.client.ApiClient();
        apiClient.setApiKey(apiKey);
        return new ControlApi(apiClient);
    }

    /**
     * Code from example:
     * https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html
     */
    @Override
    public void uploadCode(String presignedUrlStr, File file) throws IOException {
        URL presignedUrl = new URL(presignedUrlStr);
        HttpURLConnection connection = (HttpURLConnection) presignedUrl.openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/plain");
        connection.setRequestMethod("PUT");

        try (InputStream in = new FileInputStream(file);
             OutputStream os = connection.getOutputStream()) {
            in.transferTo(os);
        }

        if (connection.getResponseCode() < 200 || connection.getResponseCode() > 299) {
            throw new IOException("Failed with status " + connection.getResponseCode() + " to upload to S3: " + Strings.nullToEmpty(connection.getResponseMessage()));
        }
    }
}
