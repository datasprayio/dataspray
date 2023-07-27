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

import io.dataspray.cdk.DeployEnvironment;
import io.dataspray.cdk.dns.DnsStack;
import io.dataspray.cdk.template.BaseStack;
import io.dataspray.opennextcdk.Nextjs;
import io.dataspray.opennextcdk.NextjsDefaultsProps;
import io.dataspray.opennextcdk.NextjsDistributionPropsDefaults;
import io.dataspray.opennextcdk.NextjsDomainProps;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.constructs.Construct;

import java.util.List;

@Slf4j
public class OpenNextStack extends BaseStack {

    @Getter
    private final Nextjs nextjs;

    public OpenNextStack(Construct parent, DeployEnvironment deployEnv, Options options) {
        super(parent, "site", deployEnv);

        NextjsDomainProps domainProps;
        switch (deployEnv) {
            case PRODUCTION:
                domainProps = NextjsDomainProps.builder()
                        .isExternalDomain(false)
                        .domainName(deployEnv.getDnsDomain().get())
                        .alternateNames(List.of("www." + deployEnv.getDnsDomain().get()))
                        .hostedZone(options.getDnsStack().getDnsZone())
                        .build();
                break;
            case STAGING:
                domainProps = NextjsDomainProps.builder()
                        .isExternalDomain(false)
                        .domainName(deployEnv.getDnsDomain().get())
                        .hostedZone(options.getDnsStack().getDnsZone())
                        .build();
                break;
            case SELFHOST:
                domainProps = NextjsDomainProps.builder()
                        .isExternalDomain(false)
                        .domainName(options.getDnsStack().getDnsDomainParam().getValueAsString())
                        .hostedZone(options.getDnsStack().getDnsZone())
                        .build();
                break;
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
