package io.dataspray.backend.deploy;

import io.dataspray.lambda.deploy.BaseStack;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.amazon.awscdk.services.route53.HostedZone;
import software.constructs.Construct;

@Slf4j
public class DnsStack extends BaseStack {
    private static final String DOMAIN = "dataspray.io";
    private static final String STACK_NAME = "dns-" + DOMAIN.replaceAll("[^a-zA-Z]", "-");

    protected final HostedZone dnsZone;

    public DnsStack(Construct parent) {
        super(parent, STACK_NAME);

        dnsZone = HostedZone.Builder.create(this, DOMAIN + "-zone")
                .zoneName(DOMAIN)
                .build();
    }

    public static void main(String[] args) {
        App app = new App();
        new DnsStack(app);
        app.synth();
    }
}
