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

package io.dataspray.cli;

import com.google.common.collect.ImmutableList;
import io.dataspray.core.StreamRuntime.Organization;
import lombok.Value;

import java.util.Optional;

public interface CliConfig {

    ConfigState getConfigState();

    /**
     * Get API Key.
     * <p>
     * Organization is determined in the following order:
     *     <ol>
     *         <li>Organization name passed as parameter</li>
     *         <li>Organization name from environment variable</li>
     *         <li>Organization name defined as default</li>
     *         </ol>
     * </p>
     */
    Organization getOrganization(Optional<String> organizationOpt);

    void setOrganization(String organizationName, String apiKey, Optional<String> endpointOpt);

    Optional<String> getDefaultOrganization();

    void setDefaultOrganization(String organizationName);

    @Value
    class ConfigState {
        String configFilePath;
        Optional<String> defaultOrganization;
        ImmutableList<Organization> organizations;
    }
}
