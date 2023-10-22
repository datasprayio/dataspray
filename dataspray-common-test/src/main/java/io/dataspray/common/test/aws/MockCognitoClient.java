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

package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.mockito.Mockito;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockCognitoClient {
    public static final String MOCK_COGNITO_USERS = "mock-cognito-users";

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.cognito.mock.enable", stringValue = "true")
    @Named(MOCK_COGNITO_USERS)
    public ConcurrentMap<String, UserType> getMockCognitoUsers() {
        return Maps.newConcurrentMap();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.cognito.mock.enable", stringValue = "true")
    public CognitoIdentityProviderClient getCognitoClient(@Named(MOCK_COGNITO_USERS) ConcurrentMap<String, UserType> usersByUsername) {
        CognitoIdentityProviderClient mock = Mockito.mock(CognitoIdentityProviderClient.class);

        when(mock.adminCreateUser(Mockito.<AdminCreateUserRequest>any()))
                .thenAnswer(invocation -> {
                    AdminCreateUserRequest request = invocation.getArgument(0, AdminCreateUserRequest.class);
                    UserType newUser = UserType.builder()
                            .username(request.username())
                            .enabled(true)
                            .userCreateDate(Instant.now())
                            .userStatus(UserStatusType.CONFIRMED).build();
                    if (usersByUsername.putIfAbsent(request.username(), newUser) != null) {
                        throw UsernameExistsException.builder().build();
                    }
                    return AdminCreateUserResponse.builder()
                            .user(newUser).build();
                });
        when(mock.adminGetUser(Mockito.<AdminGetUserRequest>any()))
                .thenAnswer(invocation -> {
                    AdminGetUserRequest request = invocation.getArgument(0, AdminGetUserRequest.class);
                    UserType user = usersByUsername.get(request.username());
                    if (user == null) {
                        throw UserNotFoundException.builder().build();
                    }
                    return AdminGetUserResponse.builder()
                            .username(user.username())
                            .enabled(user.enabled())
                            .userCreateDate(user.userCreateDate())
                            .userStatus(user.userStatus()).build();
                });

        return mock;
    }

    public static class Profile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.cognito.mock.enable", "true");
        }
    }
}
