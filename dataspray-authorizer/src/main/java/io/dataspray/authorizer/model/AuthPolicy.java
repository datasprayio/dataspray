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

import jakarta.annotation.Nullable;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

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
 * Number of times I already tried and gave up: 2 (Increment as needed)
 *
 * @author Jack Kohn
 * @see <a
 * href="https://github.com/awslabs/aws-apigateway-lambda-authorizer-blueprints/blob/master/blueprints/java/src/io/AuthPolicy.java">Original</a>
 * @see <a
 * href="https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-lambda-authorizer-output.html">Documentation</a>
 */
@ToString
public class AuthPolicy {

    // IAM Policy Constants
    public static final String VERSION = "Version";
    public static final String STATEMENT = "Statement";
    public static final String EFFECT = "Effect";
    public static final String ACTION = "Action";
    public static final String NOT_ACTION = "NotAction";
    public static final String RESOURCE = "Resource";
    public static final String NOT_RESOURCE = "NotResource";
    public static final String CONDITION = "Condition";

    String principalId;
    transient AuthPolicy.PolicyDocument policyDocumentObject;
    Map<String, Object> policyDocument;
    @Nullable
    String usageIdentifierKey;
    Map<String, String> context;

    public AuthPolicy(String principalId, AuthPolicy.PolicyDocument policyDocumentObject, Optional<String> usageIdentifierKeyOpt, Map<String, String> context) {
        this.principalId = checkNotNull(principalId);
        this.usageIdentifierKey = checkNotNull(usageIdentifierKeyOpt).orElse(null);
        this.context = checkNotNull(context);
        this.policyDocumentObject = checkNotNull(policyDocumentObject);
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    /**
     * IAM Policies use capitalized field names, but Lambda by default will serialize object members using camel case
     *
     * This method implements a custom serializer to return the IAM Policy as a well-formed JSON document, with the
     * correct field names
     *
     * @return IAM Policy as a well-formed JSON document
     */
    public Map<String, Object> getPolicyDocument() {
        // When this object is deserialized using GSON,
        // policyDocumentObject is null as it's transient.
        // In this case policyDocument may already be populated
        // via reflection so return it
        if (policyDocumentObject == null) {
            return policyDocument;
        }
        Map<String, Object> serializablePolicy = new HashMap<>();
        serializablePolicy.put(VERSION, policyDocumentObject.Version);
        Statement[] statements = policyDocumentObject.getStatement();
        Map<String, Object>[] serializableStatementArray = new Map[statements.length];
        for (int i = 0; i < statements.length; i++) {
            Map<String, Object> serializableStatement = new HashMap<>();
            AuthPolicy.Statement statement = statements[i];
            serializableStatement.put(EFFECT, statement.Effect);
            serializableStatement.put(ACTION, statement.Action);
            serializableStatement.put(RESOURCE, statement.getResource());
            serializableStatement.put(CONDITION, statement.getCondition());
            serializableStatementArray[i] = serializableStatement;
        }
        serializablePolicy.put(STATEMENT, serializableStatementArray);
        return serializablePolicy;
    }

    public void setPolicyDocument(PolicyDocument policyDocumentObject) {
        this.policyDocumentObject = checkNotNull(policyDocumentObject);
    }

    @Nullable
    public String getUsageIdentifierKey() {
        return usageIdentifierKey;
    }

    public void setUsageIdentifierKey(Optional<String> usageIdentifierKeyOpt) {
        this.usageIdentifierKey = usageIdentifierKeyOpt.orElse(null);
    }

    public Map<String, String> getContext() {
        return context;
    }

    public void addToContext(String key, String value) {
        if (this.context == null) {
            this.context = new HashMap<>();
        }
        this.context.put(key, value);
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    /**
     * PolicyDocument represents an IAM Policy, specifically for the execute-api:Invoke action
     * in the context of a API Gateway Authorizer
     *
     * Initialize the PolicyDocument with
     * the region where the RestApi is configured,
     * the AWS Account ID that owns the RestApi,
     * the RestApi identifier
     * and the Stage on the RestApi that the Policy will apply to
     */
    public static class PolicyDocument {

        static final String EXECUTE_API_ARN_FORMAT = "arn:aws:execute-api:%s:%s:%s/%s/%s/%s";

        String Version = "2012-10-17"; // override if necessary

        private List<Statement> statements;

        // context metadata
        transient String region;
        transient String awsAccountId;
        transient String restApiId;
        transient String stage;

        /**
         * Creates a new PolicyDocument with the given context
         *
         * @param region the region where the RestApi is configured
         * @param awsAccountId the AWS Account ID that owns the RestApi
         * @param restApiId the RestApi identifier
         * @param stage and the Stage on the RestApi that the Policy will apply to
         */
        public PolicyDocument(String region, String awsAccountId, String restApiId, String stage) {
            this.region = region;
            this.awsAccountId = awsAccountId;
            this.restApiId = restApiId;
            this.stage = stage;
            this.statements = new ArrayList<>();
        }

        public String getVersion() {
            return Version;
        }

        public void setVersion(String version) {
            Version = version;
        }

        public AuthPolicy.Statement[] getStatement() {
            return statements.toArray(new AuthPolicy.Statement[statements.size()]);
        }

        public Statement createStatement(Effect effect, HttpMethod httpMethod, String resourcePath) {
            Statement statement = Statement.getEmptyInvokeStatement(effect);
            statements.add(statement);
            return statement;
        }

    }

    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, ALL
    }

    public enum Effect {
        ALLOW, DENY
    }

    public static class Statement {

        String Effect;
        String Action;
        Map<String, Map<String, Object>> Condition;

        private List<String> resourceList;

        public Statement() {

        }

        public Statement(Effect effect, String action, List<String> resourceList, Map<String, Map<String, Object>> condition) {
            this.Effect = effect == AuthPolicy.Effect.ALLOW ? "Allow" : "Deny";
            this.Action = action;
            this.resourceList = resourceList;
            this.Condition = condition;
        }

        public static Statement getEmptyInvokeStatement(Effect effect) {
            return new Statement(effect, "execute-api:Invoke", new ArrayList<>(), new HashMap<>());
        }

        public String getEffect() {
            return Effect;
        }

        public void setEffect(Effect effect) {
            this.Effect = effect == AuthPolicy.Effect.ALLOW ? "Allow" : "Deny";
        }

        public String getAction() {
            return Action;
        }

        public void setAction(String action) {
            this.Action = action;
        }

        public String[] getResource() {
            return resourceList.toArray(String[]::new);
        }

        public void addResource(String resource) {
            resourceList.add(resource);
        }

        public Map<String, Map<String, Object>> getCondition() {
            return Condition;
        }

        public void addCondition(String operator, String key, Object value) {
            Condition.put(operator, Collections.singletonMap(key, value));
        }

    }

}