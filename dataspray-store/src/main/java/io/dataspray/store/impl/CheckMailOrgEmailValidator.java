// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.store.impl;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import io.dataspray.store.EmailValidator;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Optional;

/**
 * <a href="https://check-mail.org/">External email checker service</a> for validating email addresses
 * and checking for disposable emails.
 */
@Slf4j
@ApplicationScoped
public class CheckMailOrgEmailValidator implements EmailValidator {

    @ConfigProperty(name = "emailvalidator.checkmail.enable", defaultValue = "true")
    boolean enabled;
    @ConfigProperty(name = "emailvalidator.checkmail.endpointPrefix", defaultValue = "https://mailcheck.p.rapidapi.com/?domain=")
    String endpointPrefix;
    @ConfigProperty(name = "emailvalidator.checkmail.host", defaultValue = "mailcheck.p.rapidapi.com")
    String rapidApiHost;
    @ConfigProperty(name = "emailvalidator.checkmail.apiKey")
    Optional<String> apiKeyOpt;

    @Inject
    Gson gson;

    @Override
    public EmailValidResult check(String email) {
        if (!enabled) {
            return EmailValidResult.VALID;
        }
        if (apiKeyOpt.isEmpty()) {
            log.warn("Could not check email validity, api key not set");
            return EmailValidResult.VALID;
        }

        try {
            Optional<String> domainOpt = getDomain(email);
            if (domainOpt.isEmpty()) {
                return EmailValidResult.INVALID;
            }

            String domainEscaped = URLEncoder.encode(domainOpt.get(), Charsets.UTF_8);

            HttpGet req = new HttpGet(endpointPrefix + domainEscaped);
            req.setHeader("x-rapidapi-host", rapidApiHost);
            req.setHeader("x-rapidapi-key", apiKeyOpt.get());
            String responseStr;
            try (CloseableHttpClient client = HttpClientBuilder.create().build();
                 CloseableHttpResponse res = client.execute(req)) {
                if (res.getStatusLine().getStatusCode() < 200
                    || res.getStatusLine().getStatusCode() > 299) {
                    log.warn("API returned non 200 result for email {}, status {}",
                            email, res.getStatusLine().getStatusCode());
                    return EmailValidResult.VALID;
                }
                responseStr = CharStreams.toString(new InputStreamReader(
                        res.getEntity().getContent(), Charsets.UTF_8));
            } catch (IOException ex) {
                log.warn("Failed to check email {} with io exception", email, ex);
                return EmailValidResult.VALID;
            }

            CheckMailResponse response;
            try {
                response = gson.fromJson(responseStr, CheckMailResponse.class);
            } catch (JsonSyntaxException ex) {
                log.warn("Failed to parse response for email {}: {}", email, responseStr, ex);
                return EmailValidResult.VALID;
            }

            if (response.disposable) {
                log.info("Denying email as disposable, email {} reason {}",
                        email, response.reason);
                return EmailValidResult.DISPOSABLE;
            }
            if (!response.valid || response.block) {
                log.info("Denying email as invalid, email {} reason {}",
                        email, response.reason);
                return EmailValidResult.INVALID;
            }
            return EmailValidResult.VALID;
        } catch (Exception ex) {
            log.warn("Failed to proces email {}", email, ex);
            return EmailValidResult.VALID;
        }
    }

    private Optional<String> getDomain(String email) {
        if (Strings.isNullOrEmpty(email)) {
            return Optional.empty();
        }

        int atIndex = email.indexOf("@");
        if (atIndex == -1) {
            return Optional.empty();
        }

        String domain = email.substring(atIndex + 1).trim();

        if (Strings.isNullOrEmpty(domain)) {
            return Optional.empty();
        }

        return Optional.of(domain);
    }

    /**
     * API definition
     * <p>
     *
     * @see <a href="https://check-mail.org/get-started/">https://check-mail.org/get-started/</a>
     * @see <a
     * href="https://rapidapi.com/Top-Rated/api/e-mail-check-invalid-or-disposable-domain">https://rapidapi.com/Top-Rated/api/e-mail-check-invalid-or-disposable-domain</a>
     */
    @Value
    private static class CheckMailResponse {
        @Nonnull
        @SerializedName("valid")
        Boolean valid;

        @Nonnull
        @SerializedName("block")
        Boolean block;

        @Nonnull
        @SerializedName("disposable")
        Boolean disposable;

        @SerializedName("reason")
        String reason;

        // Remainder left out as it is not needed
    }
}
