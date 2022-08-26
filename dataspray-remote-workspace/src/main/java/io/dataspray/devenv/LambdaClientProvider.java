package io.dataspray.devenv;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.LambdaClient;

@Slf4j
public class LambdaClientProvider {
    private static volatile LambdaClient client;

    public static LambdaClient get() {
        if (client == null) {
            synchronized (LambdaClientProvider.class) {
                if (client == null) {
                    client = LambdaClient.builder()
                            .build();
                }
            }
        }
        return client;
    }
}
