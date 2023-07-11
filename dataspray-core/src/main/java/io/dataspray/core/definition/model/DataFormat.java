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

package io.dataspray.core.definition.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class DataFormat extends Item {

    @Nonnull
    DataFormat.Serde serde;

    public boolean isSerdeBinary() {
        return Serde.BINARY.equals(serde);
    }

    public boolean isSerdeString() {
        return Serde.STRING.equals(serde);
    }

    public boolean isSerdeJson() {
        return Serde.JSON.equals(serde);
    }

    public boolean isSerdeProtobuf() {
        return Serde.PROTOBUF.equals(serde);
    }

    public boolean isSerdeAvro() {
        return Serde.AVRO.equals(serde);
    }

    @Getter
    @AllArgsConstructor
    public enum Serde {
        BINARY,
        STRING,
        JSON,
        PROTOBUF,
        AVRO
    }
}
