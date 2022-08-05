package io.dataspray.stream.web;

import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.jersey.JerseyLambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.ContainerConfig;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Wraps JAX-RS resources and automatically exposes them via Lambda
 *
 * Based on guide from https://github.com/awslabs/aws-serverless-java-container/wiki/Quick-start---Jersey
 */
@Slf4j
public class LambdaRunner implements RequestStreamHandler {
    private final JerseyLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    public LambdaRunner() {
        this.handler = JerseyLambdaContainerHandler.getAwsProxyHandler(new StreamApplication());
        ContainerConfig containerConfig = LambdaContainerHandler.getContainerConfig();
        containerConfig.setDefaultContentCharset(Charsets.UTF_8.name());
        containerConfig.addBinaryContentTypes("application/zip");
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        handler.proxyStream(inputStream, outputStream, context);
    }

}
