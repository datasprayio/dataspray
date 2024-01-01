// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.authorizer;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class AuthorizerConstants {

    public static final String CONTEXT_KEY_USER_EMAIL = "userEmail";
    /** Value will hold comma delimited list of organization names */
    public static final String CONTEXT_KEY_ORGANIZATION_NAMES = "organizationName";

    private AuthorizerConstants() {
        // Disallow ctor
    }
}
