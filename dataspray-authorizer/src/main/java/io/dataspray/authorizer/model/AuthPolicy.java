/*
 * Copyright 2015-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package io.dataspray.authorizer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NonNull;
import lombok.Value;

import java.util.Map;
import java.util.Optional;

/**
 * AuthPolicy receives a set of allowed and denied methods and generates a valid
 * AWS policy for the API Gateway authorizer. The constructor receives the calling
 * user principal, the AWS account ID of the API owner, and an apiOptions object.
 * The apiOptions can contain an API Gateway RestApi Id, a region for the RestApi, and a
 * stage that calls should be allowed/denied for. For example
 * <br />
 * new AuthPolicy(principalId, AuthPolicy.PolicyDocument.getDenyAllPolicy(region, awsAccountId, restApiId, stage));
 * <br />
 * WARNING: Matus do not try to convert this to GSON serializable.
 * Number of times I already tried and gave up: 3 (Increment as needed)
 * Latest roadblock: Quarkus uses Jackson for RequestHandler serde; RequestStreamHandler cannot control content-type
 *
 * @author Jack Kohn
 * @see <a
 * href="https://github.com/awslabs/aws-apigateway-lambda-authorizer-blueprints/blob/master/blueprints/java/src/io/AuthPolicy.java">Original</a>
 * @see <a
 * href="https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-lambda-authorizer-output.html">Documentation</a>
 */
@Value
@JsonInclude(Include.NON_EMPTY)
@RegisterForReflection
public class AuthPolicy {
    @NonNull
    String principalId;
    @NonNull
    PolicyDocument policyDocument;
    @NonNull
    @JsonProperty("usageIdentifierKey")
    @SerializedName("usageIdentifierKey")
    Optional<String> usageIdentifierKeyOpt;
    @NonNull
    Map<String, String> context;
}