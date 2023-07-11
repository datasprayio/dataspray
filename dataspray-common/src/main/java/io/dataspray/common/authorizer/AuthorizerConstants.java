// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.authorizer;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class AuthorizerConstants {

    public static final String CONTEXT_KEY_ACCOUNT_ID = "accountId";
    public static final String CONTEXT_KEY_APIKEY_VALUE = "apiKey";

    private AuthorizerConstants() {
        // Disallow ctor
    }
}
