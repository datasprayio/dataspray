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

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.google.common.collect.ImmutableSet;
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nonnull;
import lombok.NonNull;
import lombok.Value;

import java.util.Optional;

/**
 * JWT Verifier and parser for Cognito tokens.
 */
public interface CognitoJwtVerifier {

    Optional<VerifiedCognitoJwt> verify(String accessToken) throws JWTVerificationException;

    @Value
    @RegisterForReflection
    class VerifiedCognitoJwt {

        @Nonnull
        String userEmail;

        @Nonnull
        ImmutableSet<String> groupNames;

        @NonNull
        UsageKeyType usageKeyType;
    }
}
