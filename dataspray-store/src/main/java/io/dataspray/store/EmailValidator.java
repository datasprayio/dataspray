// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.store;

public interface EmailValidator {

    EmailValidResult check(String email);

    enum EmailValidResult {
        VALID,
        INVALID,
        DISPOSABLE
    }
}
