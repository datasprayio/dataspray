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

import io.dataspray.cdk.template.BaseStack;
import io.dataspray.common.DeployEnvironment;
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
import software.amazon.awscdk.services.route53.NsRecord;
import software.amazon.awscdk.services.route53.RecordSet;
import software.constructs.Construct;

import java.util.List;

@Slf4j
public class DnsStack extends BaseStack {

    @Getter
    private final CfnParameter dnsDomainParam;
    @Getter
    private final CfnParameter dnsSubdomainParam;
    @Getter
    private final CfnParameter dnsDomainZoneIdParam;
    @Getter
    private final String dnsFqdn;
    @Getter
    private final HostedZone dnsZone;
    @Getter
    private final RecordSet parentZoneDelegatingSubdomainRecordSet;

    public DnsStack(Construct parent, DeployEnvironment deployEnv) {
        super(parent, "dns", deployEnv);

        dnsDomainParam = CfnParameter.Builder.create(this, "dnsDomain")
                .description("Domain name for your app (e.g. example.com)")
                .type("String")
                .minLength(3)
                .build();
        dnsSubdomainParam = CfnParameter.Builder.create(this, "dnsSubdomain")
                .description("Optional subdomain for your app (defaults to dataspray)")
                .type("String")
                .defaultValue("dataspray")
                .build();
        dnsDomainZoneIdParam = CfnParameter.Builder.create(this, "dnsDomainZoneId")
                .description("If using a subdomain (e.g. dataspray.example.com), enter the Route53 Hosted Zone Id for the parent domain (e.g. Z104162015L8HFMCRVJ9Y) if you wish to add a NS delegating record, otherwise leave this blank.")
                .type("String")
                .defaultValue("")
                .build();
        dnsFqdn = Fn.join(
                dnsSubdomainParam.getValueAsString(),
                List.of(Fn.conditionIf(
                        // If subdomain is empty
                        CfnCondition.Builder.create(this, getConstructId("condition-empty-subdomain"))
                                .expression(Fn.conditionEquals(dnsSubdomainParam.getValueAsString(), ""))
                                .build()
                                .getLogicalId(),
                        // Then supply the domain only
                        dnsDomainParam.getValueAsString(),
                        // Else prefix the domain with a dot to separate the subdomain
                        Fn.join(".", List.of(dnsDomainParam.getValueAsString()))
                ).toString()));

        dnsZone = HostedZone.Builder.create(this, getConstructId("zone"))
                .zoneName(dnsFqdn)
                .build();

        // Fetch parent zone for creating delegate records. May not end up being used if params are not set
        IHostedZone parentZone = HostedZone.fromHostedZoneAttributes(this, getConstructId("parentZone"), HostedZoneAttributes.builder()
                .hostedZoneId(dnsDomainZoneIdParam.getValueAsString())
                .zoneName(dnsDomainParam.getValueAsString())
                .build());
        // Delegating subdomain record
        parentZoneDelegatingSubdomainRecordSet = NsRecord.Builder.create(this, getConstructId("recordset-delegating-subdomain"))
                .zone(parentZone)
                .recordName(dnsSubdomainParam.getValueAsString())
                .values(dnsZone.getHostedZoneNameServers())
                .ttl(Duration.days(2))
                .deleteExisting(false)
                .build();
        // Only create delegating record if subdomain and parent zone id are set
        CfnCondition createDelegateRecordCondition = CfnCondition.Builder.create(this, getConstructId(""))
                .expression(Fn.conditionAnd(
                        Fn.conditionNot(Fn.conditionEquals(dnsSubdomainParam, "")),
                        Fn.conditionNot(Fn.conditionEquals(dnsDomainZoneIdParam, ""))))
                .build();
        ((CfnRecordSet) parentZoneDelegatingSubdomainRecordSet.getNode().getDefaultChild())
                .getCfnOptions()
                .setCondition(createDelegateRecordCondition);
    }
}
