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

package io.dataspray.cdk.api;

import com.google.common.collect.ImmutableSet;
import io.dataspray.cdk.DatasprayStack;
import io.dataspray.cdk.dns.DnsStack;
import io.dataspray.cdk.site.NextSiteStack;
import io.dataspray.cdk.template.BaseStack;
import io.dataspray.cdk.template.FunctionStack;
import io.dataspray.common.DeployEnvironment;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.constructs.Construct;

import java.util.Optional;

@Getter
public abstract class ApiFunctionStack extends FunctionStack {

    public static final String CORS_ALLOW_HEADERS = "Content-Type,X-Amz-Date,Authorization";
    public static final String CORS_ALLOW_METHODS = "*";

    private final Options options;
    private final ImmutableSet<String> apiTags;
    private final String apiFunctionName;
    private final SingletonFunction apiFunction;

    public ApiFunctionStack(Construct parent, Options options) {
        super(parent, "web-" + options.getFunctionName(), options.getDeployEnv());

        this.options = options;
        apiTags = options.getApiTags();
        apiFunctionName = options.getFunctionName() + options.getDeployEnv().getSuffix();
        apiFunction = addSingletonFunction(
                getConstructId("lambda"),
                apiFunctionName,
                options.getCodeZip(),
                options.getMemorySize(),
                options.getMemorySizeNative());

        // Setup CORS using Quarkus Jakarta CORS filter
        // https://quarkus.io/guides/security-cors
        String corsAllowedOrigin = getCorsAllowOrigins(this);
        DatasprayStack.setConfigProperty(apiFunction, "quarkus.http.cors", "true");
        DatasprayStack.setConfigProperty(apiFunction, "quarkus.http.cors.headers", CORS_ALLOW_HEADERS);
        DatasprayStack.setConfigProperty(apiFunction, "quarkus.http.cors.methods", CORS_ALLOW_METHODS);
        DatasprayStack.setConfigProperty(apiFunction, "quarkus.http.cors.origins", corsAllowedOrigin);
    }

    public String getCorsAllowOrigins(final BaseStack stack) {
        return switch (getDeployEnv()) {
            case STAGING, TEST -> "*";
            case PRODUCTION, SELFHOST -> options.getCorsForSite()
                    .map(subdomain -> "https://" + subdomain + "." + DnsStack.createFqdn(stack, getDeployEnv()))
                    .orElseGet(() -> "https://" + DnsStack.createFqdn(stack, getDeployEnv()));
        };
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        DeployEnvironment deployEnv;
        @NonNull
        ImmutableSet<String> apiTags;
        @NonNull
        String functionName;
        @NonNull
        String codeZip;
        @lombok.Builder.Default
        int memorySize = 256;
        @lombok.Builder.Default
        int memorySizeNative = 128;
        @Nullable
        NextSiteStack corsForSite;

        public Optional<NextSiteStack> getCorsForSite() {
            return Optional.ofNullable(corsForSite);
        }
    }
}
