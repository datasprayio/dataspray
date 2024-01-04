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

package io.dataspray.cdk.template;

import com.google.common.collect.Maps;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.common.StringUtil;
import lombok.Getter;
import software.amazon.awscdk.CfnElement;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.TagProps;
import software.amazon.awscdk.Tags;
import software.constructs.Construct;

import java.util.Map;
import java.util.function.Function;

public abstract class BaseStack extends Stack {

    @Getter
    private final DeployEnvironment deployEnv;
    private final String baseConstructId;
    private final Map<String, CfnParameter> parameters = Maps.newHashMap();
    private final Map<String, CfnElement> constructs = Maps.newHashMap();

    public BaseStack(Construct parent, String constructIdSuffix, DeployEnvironment deployEnv) {
        super(parent,
                DeployEnvironment.RESOURCE_PREFIX + constructIdSuffix + deployEnv.getSuffix(),
                StackProps.builder()
                        .env(Environment.builder()
                                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                                .region(System.getenv("CDK_DEFAULT_REGION"))
                                .build())
                        .build());
        this.deployEnv = deployEnv;
        this.baseConstructId = DeployEnvironment.RESOURCE_PREFIX + constructIdSuffix;

        Tags.of(this).add("dataspray-construct-id", this.baseConstructId, TagProps.builder()
                .applyToLaunchedInstances(true)
                .priority(1000)
                .build());
        Tags.of(this).add("dataspray-stack-id", getStackId(), TagProps.builder()
                .applyToLaunchedInstances(true)
                .priority(1000)
                .build());
        Tags.of(this).add("dataspray-env", getDeployEnv().name(), TagProps.builder()
                .applyToLaunchedInstances(true)
                .priority(1000)
                .build());
    }

    public String getConstructId(String name) {
        return baseConstructId + "-" + name + deployEnv.getSuffix();
    }

    public String getConstructIdCamelCase(String name) {
        return StringUtil.camelCase(getConstructId(name), true);
    }

    /**
     * Utility method to get or create a new Stack parameter to ensure we don't create duplicates.
     */
    public CfnParameter getOrCreateParameter(String parameterName, Function<CfnParameter.Builder, CfnParameter.Builder> builder) {
        return parameters.computeIfAbsent(parameterName,
                k -> builder.apply(CfnParameter.Builder.create(this, parameterName)).build());
    }

    /**
     * Utility method to get or create a new Stack parameter to ensure we don't create duplicates.
     */
    public <T extends CfnElement> T getOrCreateConstruct(String constructId, Function<String, T> supplier) {
        //noinspection unchecked
        return (T) constructs.computeIfAbsent(constructId, supplier);
    }
}
