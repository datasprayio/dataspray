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

package io.dataspray.common;

import com.google.common.base.Strings;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

import static java.util.function.Predicate.not;

@ApplicationScoped
public class VersionUtil {

    public String getVersion() {
        return getVersionFromManifest()
                .or(this::getVersionFromQuarkusConfig)
                .orElse("UNKNOWN");
    }

    private Optional<String> getVersionFromManifest() {
        return Optional.ofNullable(Strings.emptyToNull(getClass().getPackage().getImplementationVersion()))
                .filter(not("0.0.1-SNAPSHOT"::equals));
    }

    private Optional<String> getVersionFromQuarkusConfig() {
        try {
            return Optional.ofNullable(Strings.emptyToNull(org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getValue("quarkus.application.version", String.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
