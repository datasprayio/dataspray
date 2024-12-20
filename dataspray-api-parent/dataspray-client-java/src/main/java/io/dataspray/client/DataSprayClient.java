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

package io.dataspray.client;

import com.google.common.base.Function;
import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.control.client.HealthApi;
import io.dataspray.stream.control.client.model.DeployRequest;
import io.dataspray.stream.control.client.model.TaskVersion;
import io.dataspray.stream.ingest.client.IngestApi;

import java.io.File;

public interface DataSprayClient {

    static DataSprayClient get(Access access) {
        return new DataSprayClientImpl(access);
    }

    HealthApi health();

    IngestApi ingest();

    ControlApi control();

    TaskVersion uploadAndPublish(
            String organizationName,
            String taskId,
            File codeZipFile,
            Function<String, DeployRequest> codeUrlToDeployRequest
    );
}
