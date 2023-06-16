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

package io.dataspray.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.dataspray.authorizer.model.AuthPolicy;
import io.dataspray.authorizer.model.TokenAuthorizerContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatasprayAuthorizer implements RequestHandler<TokenAuthorizerContext, AuthPolicy> {

    @Override
    public AuthPolicy handleRequest(TokenAuthorizerContext input, Context context) {

        String token = input.getAuthorizationToken();
        // validate the incoming token
        // and produce the principal user identifier associated with the token

        // this could be accomplished in a number of ways:
        // 1. Call out to OAuth provider
        // 2. Decode a JWT token in-line
        // 3. Lookup in a self-managed DB
        String principalId = "xxxx";

        // if the client token is not recognized or invalid
        // you can send a 401 Unauthorized response to the client by failing like so:
        // throw new RuntimeException("Unauthorized");

        // if the token is valid, a policy should be generated which will allow or deny access to the client

        // if access is denied, the client will receive a 403 Access Denied response
        // if access is allowed, API Gateway will proceed with the back-end integration configured on the method that was called

        String methodArn = input.getMethodArn();
        String[] arnPartials = methodArn.split(":");
        String region = arnPartials[3];
        String awsAccountId = arnPartials[4];
        String[] apiGatewayArnPartials = arnPartials[5].split("/");
        String restApiId = apiGatewayArnPartials[0];
        String stage = apiGatewayArnPartials[1];
        String httpMethod = apiGatewayArnPartials[2];
        String resource = ""; // root resource
        if (apiGatewayArnPartials.length == 4) {
            resource = apiGatewayArnPartials[3];
        }

        // this function must generate a policy that is associated with the recognized principal user identifier.
        // depending on your use case, you might store policies in a DB, or generate them on the fly

        // keep in mind, the policy is cached for 5 minutes by default (TTL is configurable in the authorizer)
        // and will apply to subsequent calls to any method/resource in the RestApi
        // made with the same token

        // the example policy below denies access to all resources in the RestApi
        return new AuthPolicy(principalId, AuthPolicy.PolicyDocument.getDenyAllPolicy(region, awsAccountId, restApiId, stage));
    }
}
