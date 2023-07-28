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

package io.dataspray.cdk.site;

import io.dataspray.cdk.dns.DnsStack;
import io.dataspray.cdk.template.BaseStack;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.opennextcdk.Nextjs;
import io.dataspray.opennextcdk.NextjsDefaultsProps;
import io.dataspray.opennextcdk.NextjsDistributionPropsDefaults;
import io.dataspray.opennextcdk.NextjsDomainProps;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.Fn;
import software.constructs.Construct;

import java.util.List;

@Slf4j
public class OpenNextStack extends BaseStack {

    @Getter
    private final Nextjs nextjs;

    public OpenNextStack(Construct parent, DeployEnvironment deployEnv, Options options) {
        super(parent, "site", deployEnv);

        String fqdn = options.getDnsStack().createFqdn(this);

        final NextjsDomainProps domainProps;
        switch (deployEnv) {
            case PRODUCTION:
                domainProps = NextjsDomainProps.builder()
                        .isExternalDomain(false)
                        .domainName(fqdn)
                        .domainAlias(Fn.join("www.", List.of(fqdn)))
                        .hostedZone(options.getDnsStack().getDnsZone())
                        .build();
                break;
            case STAGING:
            case SELFHOST:
                domainProps = NextjsDomainProps.builder()
                        .isExternalDomain(false)
                        .domainName(fqdn)
                        .hostedZone(options.getDnsStack().getDnsZone())
                        .build();
                break;
            case TEST:
                throw new RuntimeException("Cannot synthesize using " + deployEnv.name() + " env");
            default:
                throw new RuntimeException("Unknown env: " + deployEnv);
        }
        nextjs = Nextjs.Builder.create(this, getConstructId("nextjs"))
                .openNextPath(options.openNextDir)
                .defaults(NextjsDefaultsProps.builder()
                        .distribution(NextjsDistributionPropsDefaults.builder()
                                .customDomain(domainProps)
                                .stackPrefix("ds-")
                                .build())
                        .build())
                .build();
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        DnsStack dnsStack;
        @NonNull
        String openNextDir;
    }
}
