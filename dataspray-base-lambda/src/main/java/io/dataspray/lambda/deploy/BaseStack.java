package io.dataspray.lambda.deploy;

import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.TagProps;
import software.amazon.awscdk.Tags;
import software.constructs.Construct;

public abstract class BaseStack extends Stack {
    private static final String STACK_ID_TAG_NAME = "dataspray-stack-id";
    private static final String QUARKUS_FUNCTION_PATH = "target/function.zip";

    public BaseStack(Construct parent, String stackId) {
        super(parent,
                stackId,
                StackProps.builder()
                        .env(Environment.builder()
                                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                                .region(System.getenv("CDK_DEFAULT_REGION"))
                                .build())
                        .build());

        Tags.of(this).add(STACK_ID_TAG_NAME, stackId, TagProps.builder()
                .applyToLaunchedInstances(true)
                .priority(1000)
                .build());
    }
}
