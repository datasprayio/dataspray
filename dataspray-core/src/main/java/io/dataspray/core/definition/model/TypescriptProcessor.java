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

package io.dataspray.core.definition.model;

import com.google.common.collect.ImmutableSet;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.EnumSet;

import static com.google.common.base.Preconditions.checkArgument;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@RegisterForReflection
public class TypescriptProcessor extends Processor {

    private static final ImmutableSet<DataFormat.Serde> SUPPORTED_DATA_FORMATS = ImmutableSet.copyOf(EnumSet
            // Exclude the following unsupported types
            .complementOf(EnumSet.of(
                    DataFormat.Serde.PROTOBUF,
                    DataFormat.Serde.AVRO)));

    @Override
    public void initialize() {
        super.initialize();

        getStreams().forEach(stream -> {
            checkArgument(SUPPORTED_DATA_FORMATS.contains(stream.getDataFormat().getSerde()),
                    "Data format %s is not supported by Typescript processors under '%s'",
                    stream.getDataFormat().getSerde(), getName());
        });
    }
}
