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

package io.dataspray.cdk.site;

import io.dataspray.cdk.dns.DnsStack;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.opennextcdk.Nextjs;
import io.dataspray.opennextcdk.NextjsDefaultsProps;
import io.dataspray.opennextcdk.NextjsDistributionPropsDefaults;
import io.dataspray.opennextcdk.NextjsDomainProps;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

import java.util.List;
import java.util.Optional;

@Slf4j
@Getter
public class SsrNextSiteStack extends NextSiteStack {

    private final Nextjs nextjs;

    public SsrNextSiteStack(Construct parent, DeployEnvironment deployEnv, Options options) {
        super(parent, "site", deployEnv, options.getSubdomain());

        if (options.getSubdomain().isPresent()) {
            throw new NotImplementedException("Subdomains are not yet supported");
        }

        String fqdn = DnsStack.createFqdn(this, deployEnv);
        IHostedZone dnsZone = options.getDnsStack().getDnsZone(this, fqdn);

        NextjsDomainProps.Builder domainPropsBuilder = NextjsDomainProps.builder()
                .isExternalDomain(false)
                .domainName(fqdn)
                .hostedZone(dnsZone);
        if (DeployEnvironment.PRODUCTION.equals(deployEnv)) {
            domainPropsBuilder.domainAlias(Fn.join(
                    ".", List.of(
                            "www",
                            fqdn)));
        }

        nextjs = Nextjs.Builder.create(this, getConstructId("nextjs"))
                .openNextPath(options.openNextDir)
                .defaults(NextjsDefaultsProps.builder()
                        .distribution(NextjsDistributionPropsDefaults.builder()
                                .customDomain(domainPropsBuilder.build())
                                .stackPrefix(DeployEnvironment.RESOURCE_PREFIX)
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
        @lombok.Builder.Default
        Optional<String> subdomain = Optional.empty();
    }
}
