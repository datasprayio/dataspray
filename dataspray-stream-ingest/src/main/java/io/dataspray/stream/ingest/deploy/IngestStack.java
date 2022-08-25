package io.dataspray.stream.ingest.deploy;

import io.dataspray.lambda.deploy.LambdaBaseStack;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IngestStack extends LambdaBaseStack {

    public IngestStack() {
        super(Options.builder()
                .openapiYamlPath("target/openapi/api-ingest.yaml")
                .build());
    }

    public static void main(String[] args) {
        new IngestStack();
    }
}
