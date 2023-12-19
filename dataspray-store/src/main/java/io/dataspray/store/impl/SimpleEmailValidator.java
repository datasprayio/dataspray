// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.store.impl;

import io.dataspray.store.EmailValidator;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Alternative
@Priority(1)
@ApplicationScoped
@IfBuildProperty(name = SimpleEmailValidator.EMAIL_VALIDATOR_SIMPLE_ENABLED_PROP_NAME, stringValue = "true")
public class SimpleEmailValidator implements EmailValidator {

    public static final String EMAIL_VALIDATOR_SIMPLE_ENABLED_PROP_NAME = "emailvalidator.simple.enable";

    @Override
    public EmailValidResult check(String email) {
        return checkValidInternal(email)
                ? EmailValidResult.VALID
                : EmailValidResult.INVALID;
    }

    public boolean checkValidInternal(String email) {
        try {
            new InternetAddress(email).validate();
            return true;
        } catch (AddressException ex) {
            log.info("Denying email as invalid at position {} given email {}",
                    ex.getPos(), email);
            return false;
        }
    }
}
