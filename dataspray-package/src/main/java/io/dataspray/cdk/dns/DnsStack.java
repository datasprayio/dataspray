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
@Getter
public class DnsStack extends BaseStack {

    private final CfnParameter dnsDomainParam;
    @Getter
    private final CfnParameter dnsSubdomainParam;
    @Getter
    private final CfnParameter dnsDomainZoneIdParam;
    @Getter
    private final HostedZone dnsZone;
    @Getter
    private final RecordSet parentZoneDelegatingSubdomainRecordSet;

    public DnsStack(Construct parent, DeployEnvironment deployEnv) {
        super(parent, "dns", deployEnv);

        dnsDomainParam = createDnsDomainParam(this);
        dnsSubdomainParam = createDnsSubdomainParam(this, deployEnv);
        dnsDomainZoneIdParam = CfnParameter.Builder.create(this, "dnsDomainZoneId")
                .description("If using a subdomain (e.g. dataspray.example.com), enter the Route53 Hosted Zone Id for the parent domain (e.g. Z104162015L8HFMCRVJ9Y) if you wish to add a NS delegating record, otherwise leave this blank.")
                .type("String")
                .defaultValue("")
                .build();
        String dnsFqdn = createFqdn(this, dnsDomainParam, dnsSubdomainParam, deployEnv);

        dnsZone = HostedZone.Builder.create(this, getConstructId("zone"))
                .zoneName(dnsFqdn)
                .addTrailingDot(true)
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
        //noinspection DataFlowIssue
        ((CfnRecordSet) parentZoneDelegatingSubdomainRecordSet.getNode().getDefaultChild())
                .getCfnOptions()
                .setCondition(createDelegateRecordCondition);
    }

    private static CfnParameter createDnsDomainParam(final Construct scope) {
        return CfnParameter.Builder.create(scope, "dnsDomain")
                .description("Domain name for your app (e.g. example.com)")
                .type("String")
                .minLength(3)
                .build();
    }

    private static CfnParameter createDnsSubdomainParam(final Construct scope, DeployEnvironment deployEnv) {
        return CfnParameter.Builder.create(scope, "dnsSubdomain")
                .description("Optional subdomain for your app (defaults to dataspray)")
                .type("String")
                .defaultValue(DeployEnvironment.SELFHOST.equals(deployEnv) ? "dataspray" : "")
                .build();
    }

    /**
     * Creates FQDN as a reference made up of other parameters.
     * <p>
     * Due <a href="https://github.com/aws/aws-cdk/issues/26560">to a bug in CDK</a>, we need to create the FQDN in each
     * stack. Particularly, the CfnCondition is not propagated
     * across stacks if used from another stack:
     * <pre>Template error: unresolved condition dependency dsdnsconditionemptysubdomainstaging in Fn::If</pre>
     * In addition, conditions cannot have imported values including stack params so we need to re-create that too.
     * <pre>Template error: Cannot use Fn::ImportValue in Conditions</pre>
     */
    public static String createFqdn(
            final Construct scope,
            DeployEnvironment deployEnv) {
        return createFqdn(scope, createDnsDomainParam(scope), createDnsSubdomainParam(scope, deployEnv), deployEnv);
    }

    private static String createFqdn(
            final Construct scope,
            CfnParameter dnsDomainParam,
            CfnParameter dnsSubdomainParam,
            DeployEnvironment deployEnv) {
        return Fn.conditionIf(
                // If subdomain is empty
                CfnCondition.Builder.create(scope, getGlobalConstructId("condition-empty-subdomain", deployEnv))
                        .expression(Fn.conditionEquals(dnsSubdomainParam.getValueAsString(), ""))
                        .build()
                        .getLogicalId(),
                // Then supply the domain only
                dnsDomainParam.getValueAsString(),
                // Else combine subdomain with domain
                Fn.join(".", List.of(
                        dnsSubdomainParam.getValueAsString(),
                        dnsDomainParam.getValueAsString()))).toString();
    }
}
