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

package io.dataspray.store.impl;

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import io.dataspray.store.CognitoJwtVerifier;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

import static io.dataspray.store.impl.CognitoUserStore.USER_POOL_ID_PROP_NAME;

/**
 * JWT Verifier and parser for Cognito tokens.
 *
 * @see <a href="https://stackoverflow.com/a/50282130">https://stackoverflow.com/a/50282130</a>
 */
@Slf4j
@ApplicationScoped
public class CognitoJwtVerifierImpl implements CognitoJwtVerifier, RSAKeyProvider {

    @ConfigProperty(name = "aws.cognito.productionRegion", defaultValue = "us-east-1")
    String region;
    @ConfigProperty(name = USER_POOL_ID_PROP_NAME)
    String userPoolId;

    private final JWTVerifier jwtVerifier = JWT.require(Algorithm.RSA256(this)).build();
    private volatile Optional<JwkProvider> jwkProvider = Optional.empty();

    public Optional<VerifiedCognitoJwt> verify(String accessToken) throws JWTVerificationException {
        // Verify JWT and decode.
        // The JWT is in the format of a Cognito Access Token defined here:
        // Docs https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-the-access-token.html#user-pool-access-token-payload
        DecodedJWT rawJwt;
        try {
            rawJwt = jwtVerifier.verify(accessToken);
        } catch (JWTVerificationException ex) {
            log.info("Failed to verify Cognito access key {}", accessToken, ex);
            return Optional.empty();
        }

        // Fetch all group claims
        ImmutableSet<String> groupNames = Optional.ofNullable(rawJwt.getClaim("cognito:groups").asList(String.class))
                .map(ImmutableSet::copyOf)
                .orElse(ImmutableSet.of());

        // Fetch username
        String username = Strings.nullToEmpty(rawJwt.getClaim("username").asString());

        return Optional.of(new VerifiedCognitoJwt(
                username,
                groupNames));
    }

    @Override
    public RSAPublicKey getPublicKeyById(String kid) {
        if (jwkProvider.isEmpty()) {
            synchronized (this) {
                if (jwkProvider.isEmpty()) {
                    // JwkProvider internally suffixes with /.well-known/jwks.json
                    jwkProvider = Optional.of(new JwkProviderBuilder(String.format("https://cognito-idp.%s.amazonaws.com/%s",
                            region,
                            userPoolId))
                            .cached(true)
                            .build());
                }
            }
        }

        try {
            return (RSAPublicKey) jwkProvider
                    .get()
                    .get(kid)
                    .getPublicKey();
        } catch (JwkException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return null;
    }

    @Override
    public String getPrivateKeyId() {
        return null;
    }
}
