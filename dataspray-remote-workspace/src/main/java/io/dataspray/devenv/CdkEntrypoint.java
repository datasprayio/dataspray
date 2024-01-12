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

package io.dataspray.devenv;

import com.google.common.base.Strings;
import io.dataspray.devenv.DevEnvManager.DevEnv;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CdkEntrypoint {
    /** CDK entrypoint */
    public static void main(String... args) {
        if (args.length != 2 || Strings.isNullOrEmpty(args[0])) {
            log.error("Usage: <stack_id> <ecr_image_tag>");
            System.exit(1);
        }
        String stackId = args[0];
        String imageTag = args[1];

        DevEnvManager devEnvManager = new DevEnvManagerImpl();
        DevEnv devEnv = devEnvManager.create(stackId, imageTag);
        log.info("Created dev env: {}", devEnv);
    }
}
