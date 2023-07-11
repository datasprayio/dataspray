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

package io.dataspray;

import com.google.common.collect.ImmutableMap;
import io.dataspray.dns.DnsStack;
import io.dataspray.site.SiteStack;
import io.dataspray.store.AuthNzStack;
import io.dataspray.store.SingleTableStack;
import io.dataspray.stream.control.ControlStack;
import io.dataspray.stream.ingest.IngestStack;
import io.dataspray.web.AuthorizerStack;
import io.dataspray.web.BaseApiStack;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.amazon.awscdk.services.lambda.SingletonFunction;

@Slf4j
public class DatasprayStack {
    public static void main(String[] args) {
        App app = new App();

        if (args.length != 2) {
            log.error("Usage: DatasprayStack <env> <codeDir>");
            System.exit(1);
        }
        String env = args[0];
        String codeDir = args[1];

        DnsStack dnsStack = new DnsStack(app, env);
        new SiteStack(app, env);

        new SingleTableStack(app, env);
        AuthNzStack authNzStack = new AuthNzStack(app, env);
        AuthorizerStack authorizerStack = new AuthorizerStack(app, env, codeDir);

        IngestStack ingestStack = new IngestStack(app, env, codeDir);
//        TODO ingestStack.withCognitoUserPoolIdRef(authNzStack.getUserPool().getUserPoolId());
        ControlStack controlStack = new ControlStack(app, env, codeDir);
//        TODO controlStack.withCognitoUserPoolIdRef(authNzStack.getUserPool().getUserPoolId());

        BaseApiStack baseApiStack = new BaseApiStack(app, BaseApiStack.Options.builder()
                .openapiYamlPath("target/openapi/api.yaml")
                .dnsZone(dnsStack.getDnsZone())
                .authorizerStack(authorizerStack)
                .tagToFunction(ImmutableMap.of(
                        "Ingest", ingestStack.getFunction(),
                        "AuthNZ", controlStack.getFunction(),
                        "Control", controlStack.getFunction(),
                        "Health", controlStack.getFunction()))
                .build());
//        TODO baseApiStack.createUsagePlan("asdf");

        // TODO authorizer lambda; with cognito user pool id ref

        app.synth();
    }

    private void addEnvVars(
            SingletonFunction function,
            String cognitoUserPoolIdRef) {

    }

    //<editor-fold desc="disallow ctor" defaultstate="collapsed">
    private DatasprayStack() {
        // disallow ctor
    }
    //</editor-fold>
}
