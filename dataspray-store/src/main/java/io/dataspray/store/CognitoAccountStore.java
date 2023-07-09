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

package io.dataspray.store;

import com.google.common.collect.ImmutableSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AliasAttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;

import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class CognitoAccountStore implements AccountStore {

    public static final String USER_POOL_ID_PROP_NAME = "aws.cognito.user-pool-id";
    private static final String ACCOUNT_STREAM_NAMES_ATTRIBUTE = "streams";

    @ConfigProperty(name = USER_POOL_ID_PROP_NAME)
    String userPoolId;

    @Inject
    CognitoIdentityProviderClient cognitoClient;

    @Override
    public Optional<Account> getAccount(String accountId) {
        AdminGetUserResponse response;
        try {
            response = cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                    .username(accountId)
                    .userPoolId(userPoolId).build());
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }
        return Optional.of(Account.builder()
                .accountId(response.username())
                .email(response.userAttributes().stream()
                        .filter(attribute -> AliasAttributeType.EMAIL.toString().equalsIgnoreCase(attribute.name()))
                        .map(AttributeType::value)
                        .findAny().orElseThrow())
                .enabledStreamNames(response.userAttributes().stream()
                        .filter(attribute -> ACCOUNT_STREAM_NAMES_ATTRIBUTE.equalsIgnoreCase(attribute.name()))
                        .map(AttributeType::value)
                        .flatMap(setStr -> Stream.of(setStr.split(",")))
                        .collect(ImmutableSet.toImmutableSet()))
                .build());
    }

    @Override
    public StreamMetadata authorizeStreamPut(String accountId, String targetId, Optional<String> authKeyOpt) throws ClientErrorException {
        return getStream(accountId, targetId);
    }

    @Override
    public StreamMetadata getStream(String accountId, String targetId) throws ClientErrorException {
        return new StreamMetadata(Optional.of(EtlRetention.DEFAULT));
    }
}
