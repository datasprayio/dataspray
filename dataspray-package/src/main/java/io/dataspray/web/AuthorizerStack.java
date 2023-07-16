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

package io.dataspray.web;

import com.google.common.base.Charsets;
import io.dataspray.authorizer.Authorizer;
import io.dataspray.backend.BaseStack;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class AuthorizerStack extends BaseStack {

    @Getter
    private final SingletonFunction function;
    @Getter
    private final Role roleApiGatewayInvoke;

    public AuthorizerStack(Construct parent, String env, String codeZip) {
        super(parent, "authorizer", env);

        function = SingletonFunction.Builder.create(this, getSubConstructId("lambda"))
                .uuid(UUID.nameUUIDFromBytes(getSubConstructId("lambda").getBytes(Charsets.UTF_8)).toString())
                .functionName("authorizer-" + env)
                .code(Code.fromAsset(codeZip))
                .handler(Authorizer.class.getName() + "::handleRequest")
                .runtime(Runtime.JAVA_11)
                .architecture(Architecture.ARM_64)
                .memorySize(512)
                .timeout(Duration.seconds(30))
                .build();

        roleApiGatewayInvoke = Role.Builder.create(this, getSubConstructId("role"))
                .roleName("authorizer-role-invoke-" + env)
                .assumedBy(ServicePrincipal.Builder.create("apigateway.amazonaws.com").build())
                .inlinePolicies(Map.of("allowInvoke", PolicyDocument.Builder.create().statements(List.of(
                        PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of("lambda:InvokeFunction", "lambda:InvokeAsync"))
                                .resources(List.of(function.getFunctionArn()))
                                .build())).build())).build();
    }
}
