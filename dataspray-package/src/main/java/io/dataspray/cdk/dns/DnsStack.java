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

package io.dataspray.cdk.dns;

import io.dataspray.cdk.DeployEnvironment;
import io.dataspray.cdk.template.BaseStack;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.CfnCondition;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.services.route53.CfnRecordSet;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordSet;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.RecordType;
import software.constructs.Construct;

@Slf4j
public class DnsStack extends BaseStack {

    @Getter
    private final CfnParameter dnsDomainParam;
    @Getter
    private final CfnParameter dnsParentZoneNameParam;
    @Getter
    private final CfnParameter dnsParentZoneIdParam;
    @Getter
    private final HostedZone dnsZone;
    @Getter
    private final RecordSet parentZoneDelegatingSubdomainRecordSet;

    public DnsStack(Construct parent, DeployEnvironment deployEnv) {
        super(parent, "dns", deployEnv);

        dnsDomainParam = CfnParameter.Builder.create(this, "dnsDomain")
                .description("Fully qualified domain name for your app (e.g. dataspray.example.com)")
                .type("String")
                .defaultValue(deployEnv.getDnsDomain().orElse(null))
                .minLength(3)
                .build();

        dnsZone = HostedZone.Builder.create(this, getConstructId("zone"))
                .zoneName(dnsDomainParam.getValueAsString())
                .build();

        dnsParentZoneNameParam = CfnParameter.Builder.create(this, "dnsParentZoneName")
                .description("If using a subdomain (e.g. dataspray.example.com), enter the Route53 Hosted Zone Name for the parent domain (e.g. dataspray.io) if you wish to add a NS delegating record, otherwise leave this blank.")
                .type("String")
                .defaultValue(deployEnv.getDnsParentZoneName())
                .build();
        dnsParentZoneIdParam = CfnParameter.Builder.create(this, "dnsParentZoneId")
                .description("If using a subdomain (e.g. dataspray.example.com), enter the Route53 Hosted Zone Id for the parent domain (e.g. Z104162015L8HFMCRVJ9Y) if you wish to add a NS delegating record, otherwise leave this blank.")
                .type("String")
                .defaultValue(deployEnv.getDnsParentZoneId())
                .build();
        // Fetch parent zone for creating delegate records. May not end up being used if params are not set
        IHostedZone parentZone = HostedZone.fromHostedZoneAttributes(this, getConstructId("parentZone"), HostedZoneAttributes.builder()
                .hostedZoneId(dnsParentZoneIdParam.getValueAsString())
                .zoneName(dnsParentZoneNameParam.getValueAsString())
                .build());
        // Delegating subdomain record
        parentZoneDelegatingSubdomainRecordSet = RecordSet.Builder.create(this, getConstructId("recordset-delegating-subdomain"))
                .zone(parentZone)
                .recordType(RecordType.NS)
                .recordName(dnsDomainParam.getValueAsString())
                .target(RecordTarget.fromValues(dnsZone
                        .getHostedZoneNameServers()
                        .toArray(String[]::new)))
                .ttl(Duration.days(2))
                .deleteExisting(false)
                .build();
        // Only create delegating record if params are set
        CfnCondition createDelegateRecordCondition = CfnCondition.Builder.create(this, getConstructId(""))
                .expression(Fn.conditionAnd(
                        Fn.conditionNot(Fn.conditionEquals(dnsParentZoneIdParam, "")),
                        Fn.conditionNot(Fn.conditionEquals(dnsParentZoneNameParam, ""))))
                .build();
        ((CfnRecordSet) parentZoneDelegatingSubdomainRecordSet.getNode().getDefaultChild())
                .getCfnOptions()
                .setCondition(createDelegateRecordCondition);
    }
}
