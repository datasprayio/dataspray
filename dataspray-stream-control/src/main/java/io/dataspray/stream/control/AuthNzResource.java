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

package io.dataspray.stream.control;

import io.dataspray.store.ApiAccessStore;
import io.dataspray.stream.control.model.ApiKeyWithSecret;
import io.dataspray.stream.control.model.ApiKeys;
import io.dataspray.web.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;

@Slf4j
@ApplicationScoped
public class AuthNzResource extends AbstractResource implements AuthNzApi {

    @Inject
    ApiAccessStore apiAccessStore;

    @Override
    public ApiKeyWithSecret createApiKey() {
        throw new NotImplementedException();
    }

    @Override
    public ApiKeyWithSecret getApiKeySecret(String apiKeyId) {
        throw new NotImplementedException();
    }

    @Override
    public ApiKeys listApiKeys() {
        throw new NotImplementedException();
    }

    @Override
    public void revokeApiKey(String apiKeyId) {
        throw new NotImplementedException();
    }
}
