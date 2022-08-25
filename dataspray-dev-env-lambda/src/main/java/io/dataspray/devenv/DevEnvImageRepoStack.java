package io.dataspray.devenv;

import com.google.common.collect.ImmutableList;
import lombok.Value;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.TagProps;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.TagMutability;
import software.constructs.Construct;

@Value
public class DevEnvImageRepoStack extends Stack {
    private static final String STACK_ID_TAG_VALUE = "dev-env-image-repo";

    Repository repo;

    public DevEnvImageRepoStack(Construct parent, String stackId, StackProps props) {
        super(parent, stackId, props);

        repo = Repository.Builder.create(this, stackId + "-repo")
                .removalPolicy(RemovalPolicy.DESTROY)
                .imageTagMutability(TagMutability.MUTABLE)
                .lifecycleRules(ImmutableList.of(LifecycleRule.builder()
                        .maxImageCount(30)
                        .rulePriority(10)
                        .build()))
                .build();

        Tags.of(this).add(DevEnvRunnerStack.STACK_ID_TAG_NAME, STACK_ID_TAG_VALUE, TagProps.builder()
                .applyToLaunchedInstances(true)
                .priority(1000)
                .build());
    }
}
