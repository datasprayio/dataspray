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

import com.google.common.collect.ImmutableList;
import io.dataspray.cdk.dns.DnsStack;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.nextexportcdk.NextjsExportS3DynamicRoutingDistributionProps;
import io.dataspray.nextexportcdk.NextjsExportS3DynamicRoutingSite;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget;
import software.constructs.Construct;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Slf4j
@Getter
public class SsgNextSiteStack extends NextSiteStack {

    private final Certificate certificate;
    private final NextjsExportS3DynamicRoutingSite nextjs;
    private final ARecord recordSetA;
    private final AaaaRecord recordSetAaaa;

    public SsgNextSiteStack(Construct parent, DeployEnvironment deployEnv, Options options) {
        super(parent, options.getIdentifier(), deployEnv, options.getSubdomain());

        String fqdn = DnsStack.createFqdn(this, deployEnv);
        String siteFqdn = options.getSubdomain().isEmpty()
                ? fqdn
                : Fn.join(".", List.of(options.getSubdomain().get(), fqdn));
        IHostedZone dnsZone = options.getDnsStack().getDnsZone(this, fqdn);

        certificate = Certificate.Builder.create(this, getConstructId("cert"))
                .domainName(siteFqdn)
                .validation(CertificateValidation.fromDns(dnsZone))
                .build();

        Path cwd = Paths.get("").toAbsolutePath();
        nextjs = NextjsExportS3DynamicRoutingSite.Builder.create(this,
                        // Not using getConstructId since NextjsExportS3DynamicRoutingSite
                        // internally prefixes with parent id
                        "nextjs-export")
                .distributionProps(NextjsExportS3DynamicRoutingDistributionProps.builder()
                        .certificate(certificate)
                        .enableIpv6(true)
                        .domainNames(ImmutableList.of(siteFqdn))
                        .build())
                .nextBuildDir(cwd.relativize(Paths.get(options.getStaticSiteDir(), ".next").toAbsolutePath()).toString())
                .nextExportPath(cwd.relativize(Paths.get(options.getStaticSiteDir(), "out").toAbsolutePath()).toString())
                .build();

        recordSetA = ARecord.Builder.create(this, getConstructId("recordset-a"))
                .zone(dnsZone)
                // Trailing dot to fix https://github.com/aws/aws-cdk/issues/26572
                .recordName(siteFqdn + ".")
                .target(RecordTarget.fromAlias(new CloudFrontTarget(nextjs.getCloudfrontDistribution())))
                .ttl(Duration.seconds(30))
                .deleteExisting(false)
                .build();
        recordSetAaaa = AaaaRecord.Builder.create(this, getConstructId("recordset-aaaa"))
                .zone(dnsZone)
                // Trailing dot to fix https://github.com/aws/aws-cdk/issues/26572
                .recordName(siteFqdn + ".")
                .target(RecordTarget.fromAlias(new CloudFrontTarget(nextjs.getCloudfrontDistribution())))
                .ttl(Duration.seconds(30))
                .deleteExisting(false)
                .build();
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        String identifier;
        @NonNull
        @lombok.Builder.Default
        Optional<String> subdomain = Optional.empty();
        @NonNull
        DnsStack dnsStack;
        @NonNull
        String staticSiteDir;
    }
}
