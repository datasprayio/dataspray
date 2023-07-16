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

package io.dataspray.site;

import io.dataspray.backend.BaseStack;
import io.dataspray.opennextcdk.Nextjs;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.constructs.Construct;

import java.nio.file.Path;

@Slf4j
public class OpenNextStack extends BaseStack {

    public OpenNextStack(Construct parent, String env, Options options) {
        super(parent, "site", env);

        // Find .open-next path
        // For now the Nextjs construct actually needs to parent directory
        Path openNextParentPath = Path.of(options.openNextDir).getParent();

        Nextjs.Builder.create(this, getConstructId())
                .nextjsPath(openNextParentPath.toString())
                // Don't build it, just use the .open-next directory
                .isPlaceholder(true)
                .build();
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        String domain;
        @NonNull
        String openNextDir;
    }
}