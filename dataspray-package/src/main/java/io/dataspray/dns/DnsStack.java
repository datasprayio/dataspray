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

package io.dataspray.dns;

import io.dataspray.backend.BaseStack;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.services.route53.HostedZone;
import software.constructs.Construct;

@Slf4j
public class DnsStack extends BaseStack {

    @Getter
    private final CfnParameter domainParam;
    @Getter
    private final HostedZone dnsZone;

    public DnsStack(Construct parent, String env) {
        super(parent, "dns", env);

        domainParam = CfnParameter.Builder.create(this, getSubConstructId("param-domain"))
                .type("String")
                .defaultValue("dataspray.io")
                .description("Domain name to create DNS zone for")
                .build();

        dnsZone = HostedZone.Builder.create(this, getSubConstructId("zone"))
                .zoneName(domainParam.getValueAsString())
                .build();
    }
}
