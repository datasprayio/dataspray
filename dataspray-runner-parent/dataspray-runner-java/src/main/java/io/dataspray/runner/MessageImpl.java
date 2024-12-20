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

package io.dataspray.runner;

public class MessageImpl<T> implements Message<T> {
    private final MessageMetadata metadata;
    private final T data;

    public MessageImpl(MessageMetadata metadata, T data) {
        this.metadata = metadata;
        this.data = data;
    }

    @Override
    public StoreType getStoreType() {
        return metadata.getStoreType();
    }

    @Override
    public String getStoreName() {
        return metadata.getStoreName();
    }

    @Override
    public String getStreamName() {
        return metadata.getStreamName();
    }

    @Override
    public String getKey() {
        return metadata.getKey();
    }

    @Override
    public String getId() {
        return metadata.getId();
    }

    @Override
    public T getData() {
        return data;
    }
}
