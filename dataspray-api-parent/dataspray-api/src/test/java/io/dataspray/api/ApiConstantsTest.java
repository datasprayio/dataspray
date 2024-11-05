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

package io.dataspray.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApiConstantsTest {

    Map<String, Object> api;

    @BeforeEach
    void beforeEach() throws Exception {
        try (FileReader fileReader = new FileReader("target/generated-sources/api/openapi/openapi.yaml")) {
            api = new Yaml().load(fileReader);
        }
    }

    @Test
    void testTopLevelPaths() throws Exception {
        //noinspection unchecked
        Set<String> actual = ((Map<String, Object>) api.get("paths"))
                .keySet()
                .stream()
                .map(path -> {
                    String[] split = path.split("/");
                    // /v1/<top-level>
                    return "/" + split[1] + "/" + split[2];
                })
                .collect(Collectors.toSet());

        assertEquals(Set.copyOf(ApiConstants.TOP_LEVEL_PATHS), actual, "Update TOP_LEVEL_PATHS in ApiConstants");
    }

    @Test
    void testModelNames() throws Exception {
        //noinspection unchecked
        Map<String, Object> components = (Map<String, Object>) api.get("components");
        //noinspection unchecked
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        schemas.keySet().forEach(model -> {
            // AWS ApiGateway requires all Models to be alphanumeric, but OpenAPI generates names on the fly when you
            // nest an object within an object by separating it with an underscore. This test ensures that all models
            // are alphanumeric. Otherwise you will get an error during CDK deploy:
            // > Unable to create model for 'DeployRequest_dynamoState': Model name must be alphanumeric: DeployRequest_dynamoState
            assertTrue(model.matches("^[a-zA-Z0-9]+$"), "Model names must be alphanumeric, found: '" + model + "'. Likely caused by nested objects, decouple and use $ref.");
        });
    }
}