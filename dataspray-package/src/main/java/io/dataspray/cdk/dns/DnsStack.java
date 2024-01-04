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

    private final String dnsZoneId;

    public DnsStack(Construct parent, DeployEnvironment deployEnv) {
        super(parent, "dns", deployEnv);

        CfnParameter dnsDomainParam = createDnsDomainParam(this);
        CfnParameter dnsSubdomainParam = createDnsSubdomainParam(this, deployEnv);
        CfnParameter dnsDomainZoneIdParam = getOrCreateParameter("dnsDomainZoneId", b -> b
                .description("If using a subdomain (e.g. dataspray.example.com), enter the Route53 Hosted Zone Id for the parent domain (e.g. Z104162015L8HFMCRVJ9Y) if you wish to add a NS delegating record, otherwise leave this blank.")
                .type("String")
                .defaultValue(""));
        String fqdn = createFqdn(this, dnsDomainParam, dnsSubdomainParam, deployEnv);

        // Cannot expose to other stacks, see getDnsZone() notes
        HostedZone dnsZone = HostedZone.Builder.create(this, getConstructId("zone"))
                .zoneName(getZoneName(fqdn))
                .addTrailingDot(false)
                .build();
        dnsZoneId = dnsZone.getHostedZoneId();

        // Fetch parent zone for creating delegate records. May not end up being used if params are not set
        IHostedZone parentZone = HostedZone.fromHostedZoneAttributes(this, getConstructId("parentZone"), HostedZoneAttributes.builder()
                .hostedZoneId(dnsDomainZoneIdParam.getValueAsString())
                .zoneName(dnsDomainParam.getValueAsString())
                .build());
        // Delegating subdomain record
        RecordSet parentZoneDelegatingSubdomainRecordSet = NsRecord.Builder.create(this, getConstructId("recordset-delegating-subdomain"))
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

    private String getZoneName(String dnsFqdn) {
        return dnsFqdn + ".";
    }

    /**
     * Get DNS Zone as a ref.
     * <p>
     * We cannot expose the HostedZone directly because it causes an export of the zone name that includes the subdomain
     * param. This subdomain param may be an empty string which CDK doesn't like. Instead, we use a hostedZone from
     * attributes including the zoneName constructed from subdomain taken as a stack param, not exported.
     */
    public IHostedZone getDnsZone(Construct scope, String fqdn) {
        return HostedZone.fromHostedZoneAttributes(scope, getConstructId("zone"), HostedZoneAttributes.builder()
                .hostedZoneId(dnsZoneId)
                .zoneName(getZoneName(fqdn))
                .build());
    }

    private static CfnParameter createDnsDomainParam(final BaseStack stack) {
        return stack.getOrCreateParameter("dnsDomain", b -> b
                .description("Domain name for your app (e.g. example.com)")
                .type("String")
                .minLength(3));
    }

    private static CfnParameter createDnsSubdomainParam(final BaseStack stack, DeployEnvironment deployEnv) {
        return stack.getOrCreateParameter("dnsSubdomain", b -> b
                .description("Optional subdomain for your app (defaults to dataspray)")
                .type("String")
                .defaultValue(DeployEnvironment.SELFHOST.equals(deployEnv) ? "dataspray" : ""));
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
            final BaseStack stack,
            DeployEnvironment deployEnv) {
        return createFqdn(stack, createDnsDomainParam(stack), createDnsSubdomainParam(stack, deployEnv), deployEnv);
    }

    private static String createFqdn(
            final BaseStack stack,
            CfnParameter dnsDomainParam,
            CfnParameter dnsSubdomainParam,
            DeployEnvironment deployEnv) {
        return Fn.conditionIf(
                // If subdomain is empty
                stack.getOrCreateConstruct(stack.getConstructId("condition-empty-subdomain"),
                                id -> CfnCondition.Builder.create(stack, id)
                                        .expression(Fn.conditionEquals(dnsSubdomainParam.getValueAsString(), ""))
                                        .build())
                        .getLogicalId(),
                // Then supply the domain only
                dnsDomainParam.getValueAsString(),
                // Else combine subdomain with domain
                Fn.join(".", List.of(
                        dnsSubdomainParam.getValueAsString(),
                        dnsDomainParam.getValueAsString()))).toString();
    }
}
