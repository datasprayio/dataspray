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

package io.dataspray.core;

import io.dataspray.client.Access;
import io.dataspray.stream.control.client.ApiException;
import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskVersion;
import io.dataspray.stream.control.client.model.TaskVersions;
import lombok.Value;

import java.util.Optional;

public interface StreamRuntime {

    void ping(Organization organization) throws ApiException;

    void statusAll(Organization organization, Project project);

    void status(Organization organization, Project project, String processorName);

    TaskVersion deploy(Organization organization, Project project, String processorName, boolean activateVersion);

    TaskStatus activateVersion(Organization organization, Project project, String processorName, String version);

    TaskStatus pause(Organization organization, Project project, String processorName);

    TaskStatus resume(Organization organization, Project project, String processorName);

    TaskVersions listVersions(Organization organization, Project project, String processorName);

    TaskStatus delete(Organization organization, Project project, String processorName);

    @Value
    class Organization {
        String name;
        String apiKey;
        Optional<String> endpoint;

        public Access toAccess() {
            return new Access(apiKey, endpoint);
        }
    }
}
