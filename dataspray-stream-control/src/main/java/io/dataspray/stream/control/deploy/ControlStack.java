package io.dataspray.stream.control.deploy;

import io.dataspray.lambda.deploy.LambdaBaseStack;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.constructs.Construct;

@Slf4j
public class ControlStack extends LambdaBaseStack {

    public ControlStack(Construct parent) {
        super(parent, Options.builder()
                .openapiYamlPath("target/openapi/api-control.yaml")
                .build());
    }

    public static void main(String[] args) {
        App app = new App();
        new ControlStack(app);
        app.synth();
    }
}
