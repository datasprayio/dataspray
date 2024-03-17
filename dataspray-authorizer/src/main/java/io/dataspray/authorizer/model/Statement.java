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

package io.dataspray.authorizer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.annotations.SerializedName;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ToString
@EqualsAndHashCode
@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@RegisterForReflection
public class Statement {

    private static final String EXECUTE_API_ARN_FORMAT = "arn:aws:execute-api:%s:%s:%s/%s/%s/%s";

    @JsonProperty("Action")
    @SerializedName("Action")
    private String action = "";
    @JsonProperty("Effect")
    @SerializedName("Effect")
    private Effect effect = Effect.DENY;
    @JsonProperty("Resource")
    @SerializedName("Resource")
    final private Set<String> resources = Sets.newHashSet();
    @JsonProperty("Condition")
    @SerializedName("Condition")
    final Map<String, ImmutableMap<String, Object>> conditions = Maps.newHashMap();

    public Statement setEffect(Effect effect) {
        this.effect = effect;
        return this;
    }

    public Statement setAction(String action) {
        this.action = action;
        return this;
    }

    public ImmutableList<String> getResources() {
        return ImmutableList.copyOf(resources);
    }

    /**
     * Helper method to create a new ARN resource with execute API action
     *
     * @param region the region where the RestApi is configured
     * @param awsAccountId the AWS Account ID that owns the RestApi
     * @param restApiId the RestApi identifier
     * @param stage and the Stage on the RestApi that the Policy will apply to
     * @param httpMethod Specific HTTP Method to allow or all
     * @param resourcePathOpt Optional resource path to allow or all
     */
    public static String getExecuteApiArn(
            String region,
            String awsAccountId,
            String restApiId,
            String stage,
            HttpMethod httpMethod,
            Optional<String> resourcePathOpt
    ) {
        String resourcePath = resourcePathOpt.orElse("*");
        // resourcePath must start with '/'
        // to specify the root resource only, resourcePath should be an empty string
        if (resourcePath.equals("/")) {
            resourcePath = "";
        }
        String resource = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        String method = httpMethod == HttpMethod.ALL ? "*" : httpMethod.name();

        return String.format(EXECUTE_API_ARN_FORMAT,
                region,
                awsAccountId,
                restApiId,
                stage,
                method,
                resource);
    }

    public Statement addResource(String resource) {
        this.resources.add(resource);
        return this;
    }

    public Statement addResources(Collection<String> resources) {
        this.resources.addAll(resources);
        return this;
    }

    public ImmutableMap<String, Map<String, Object>> getConditions() {
        return ImmutableMap.copyOf(conditions);
    }

    public Statement addCondition(String operator, String key, Object value) {
        this.conditions.put(operator, ImmutableMap.of(key, value));
        return this;
    }

}
