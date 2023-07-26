/*
 * Copyright 2023 Matus Faro
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

import io.dataspray.cdk.DeployEnvironment;
import io.dataspray.common.StringUtil;
import lombok.Getter;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.TagProps;
import software.amazon.awscdk.Tags;
import software.constructs.Construct;

public abstract class BaseStack extends Stack {

    @Getter
    private final DeployEnvironment deployEnv;
    private final String baseConstructId;

    public BaseStack(Construct parent, String constructIdSuffix, DeployEnvironment deployEnv) {
        super(parent,
                "ds-" + constructIdSuffix + deployEnv.getSuffix(),
                StackProps.builder()
                        .env(Environment.builder()
                                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                                .region(System.getenv("CDK_DEFAULT_REGION"))
                                .build())
                        .build());
        this.deployEnv = deployEnv;
        this.baseConstructId = "ds-" + constructIdSuffix;

        Tags.of(this).add("dataspray-construct-id", getConstructId(), TagProps.builder()
                .applyToLaunchedInstances(true)
                .priority(1000)
                .build());
        Tags.of(this).add("dataspray-stack-id", getStackId(), TagProps.builder()
                .applyToLaunchedInstances(true)
                .priority(1000)
                .build());
    }

    protected String getConstructId() {
        return baseConstructId + deployEnv.getSuffix();
    }

    protected String getSubConstructId(String subConstructIdSuffix) {
        return getConstructId() + "-" + subConstructIdSuffix + deployEnv.getSuffix();
    }

    protected String getSubConstructIdCamelCase(String subConstructIdSuffix) {
        return StringUtil.camelCase(getSubConstructId(subConstructIdSuffix), true);
    }
}
