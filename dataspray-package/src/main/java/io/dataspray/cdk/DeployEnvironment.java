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

package io.dataspray.cdk;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

@Getter
@AllArgsConstructor
public enum DeployEnvironment {
    PRODUCTION("",
            Optional.of("dataspray.io"),
            Optional.empty(),
            Optional.empty(),
            Optional.of("support@dataspray.io")),
    STAGING("-staging",
            Optional.of("staging.dataspray.io"),
            Optional.of("dataspray.io"),
            Optional.of("Z104172015L8HFMCRVJ9X"),
            Optional.of("support.staging@dataspray.io")),
    SELFHOST("-selfhost",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    private final String suffix;
    private final Optional<String> dnsDomain;
    private final Optional<String> dnsParentZoneName;
    private final Optional<String> dnsParentZoneId;
    private final Optional<String> sesEmail;
}
