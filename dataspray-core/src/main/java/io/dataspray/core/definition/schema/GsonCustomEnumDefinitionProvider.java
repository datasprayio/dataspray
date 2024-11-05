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

package io.dataspray.core.definition.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.CustomDefinitionProviderV2;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.github.victools.jsonschema.generator.impl.AttributeCollector;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class GsonCustomEnumDefinitionProvider implements CustomDefinitionProviderV2 {

    @Override
    public CustomDefinition provideCustomSchemaDefinition(ResolvedType javaType, SchemaGenerationContext context) {
        Object[] enumConstants = javaType.getErasedType().getEnumConstants();
        if (enumConstants == null || enumConstants.length == 0) {
            return null;
        }
        List<?> serializedJsonValues = this.getSerializedValuesFromSerializedName(javaType, enumConstants);
        if (serializedJsonValues == null) {
            return null;
        }

        ObjectNode customNode = context.getGeneratorConfig().createObjectNode()
                .put(context.getKeyword(SchemaKeyword.TAG_TYPE), context.getKeyword(SchemaKeyword.TAG_TYPE_STRING));
        AttributeCollector standardAttributeCollector = new AttributeCollector(context.getGeneratorConfig().getObjectMapper());
        standardAttributeCollector.setEnum(customNode, serializedJsonValues, context);
        return new CustomDefinition(customNode);
    }


    protected List<String> getSerializedValuesFromSerializedName(ResolvedType javaType, Object[] enumConstants) {
        try {
            boolean anyHasSerializedName = false;
            List<String> serializedJsonValues = new ArrayList<>(enumConstants.length);
            for (Object enumConstant : enumConstants) {
                String enumValueName = ((Enum<?>) enumConstant).name();
                SerializedName annotation = javaType.getErasedType()
                        .getDeclaredField(enumValueName)
                        .getAnnotation(SerializedName.class);
                if (annotation != null) {
                    anyHasSerializedName = true;
                }
                serializedJsonValues.add(annotation == null ? enumValueName : annotation.value());
            }
            return anyHasSerializedName ? serializedJsonValues : null;
        } catch (NoSuchFieldException | SecurityException ex) {
            return null;
        }
    }
}
