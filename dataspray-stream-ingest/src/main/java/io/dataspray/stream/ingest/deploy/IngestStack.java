package io.dataspray.stream.ingest.deploy;

import io.dataspray.lambda.deploy.LambdaBaseStack;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.constructs.Construct;

@Slf4j
public class IngestStack extends LambdaBaseStack {

    public IngestStack(Construct parent) {
        super(parent, Options.builder()
                .openapiYamlPath("target/openapi/api-ingest.yaml")
                .build());
    }

    public static void main(String[] args) {
        App app = new App();
        new IngestStack(app);
        app.synth();
    }
}
