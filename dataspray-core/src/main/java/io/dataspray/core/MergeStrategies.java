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

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class MergeStrategies {

    @FunctionalInterface
    public interface MergeStrategy {
        void merge(String mergeTemplateResult, Path targetAbsoluteFilePath);
    }

    @FunctionalInterface
    public interface JsonConflictResolution {
        void resolve(String key, JsonObject originalJson, JsonElement originalElement, JsonElement templateElement);
    }

    @FunctionalInterface
    public interface XmlConflictResolution {
        void resolve(Element originalElement, Element templateElement, Document targetDocument);
    }

    @Inject
    Gson gson;

    public Optional<MergeStrategy> findMergeStrategy(String fileName) {
        if (fileName.endsWith(".json" + CodegenImpl.MUSTACHE_FILE_EXTENSION_MERGE)) {
            return Optional.of(this::applyJson);
        } else if (fileName.endsWith(".gitignore" + CodegenImpl.MUSTACHE_FILE_EXTENSION_MERGE)) {
            return Optional.of(this::applyGitignore);
        } else {
            return Optional.empty();
        }
    }

    private void applyJson(String mergeTemplateResult, Path targetAbsoluteFilePath) {

        // First: read the original file
        JsonObject originalFileJson;
        try (var reader = Files.newBufferedReader(targetAbsoluteFilePath)) {
            originalFileJson = gson.fromJson(reader, JsonObject.class);
        } catch (IOException | JsonIOException ex) {
            throw new RuntimeException("Failed to read file: " + targetAbsoluteFilePath, ex);
        } catch (JsonSyntaxException ex) {
            throw new RuntimeException("Failed to parse file as JSON: " + targetAbsoluteFilePath, ex);
        }

        // Second: Then parse the mustache template result file
        JsonObject resultFileJson;
        try {
            resultFileJson = gson.fromJson(mergeTemplateResult, JsonObject.class);
        } catch (JsonSyntaxException ex) {
            throw new RuntimeException("Failed to parse generated template as JSON: " + targetAbsoluteFilePath, ex);
        }

        // Merge the two Jsons with a custom conflict resolution
        mergeJson((String key, JsonObject originalJson, JsonElement originalElement, JsonElement templateElement) -> {
                    // Handle merge conflicts
                    if (templateElement.isJsonNull()) {
                        // A null in template signals original value needs to be removed
                        originalJson.remove(key);
                    } else {
                        // Otherwise template always replaces original
                        originalJson.add(key, templateElement);
                    }
                },
                originalFileJson,
                resultFileJson);

        // Finally write out the merged Json back to the file
        try (var writer = Files.newBufferedWriter(targetAbsoluteFilePath)) {
            gson.toJson(originalFileJson, writer);
        } catch (IOException | JsonIOException ex) {
            throw new RuntimeException("Failed to read file: " + targetAbsoluteFilePath, ex);
        }
    }

    /**
     * Utility to merge two JSON objects.
     *
     * @see <a href="https://stackoverflow.com/a/34092374">Reference implementation</a>
     */
    private void mergeJson(JsonConflictResolution conflictResolution, JsonObject leftObj, JsonObject rightObj) {
        for (Map.Entry<String, JsonElement> rightEntry : rightObj.entrySet()) {
            String rightKey = rightEntry.getKey();
            JsonElement rightVal = rightEntry.getValue();
            if (leftObj.has(rightKey)) {
                // conflict
                JsonElement leftVal = leftObj.get(rightKey);
                if (leftVal.isJsonArray() && rightVal.isJsonArray()) {
                    JsonArray leftArr = leftVal.getAsJsonArray();
                    JsonArray rightArr = rightVal.getAsJsonArray();
                    // concat the arrays -- there cannot be a conflict in an array, it's just a collection of stuff
                    for (int i = 0; i < rightArr.size(); i++) {
                        leftArr.add(rightArr.get(i));
                    }
                } else if (leftVal.isJsonObject() && rightVal.isJsonObject()) {
                    // recursive merging
                    mergeJson(conflictResolution, leftVal.getAsJsonObject(), rightVal.getAsJsonObject());
                } else {
                    // not both arrays or objects, normal merge with conflict resolution
                    conflictResolution.resolve(rightKey, leftObj, leftVal, rightVal);
                }
            } else {
                // no conflict, add to the object
                leftObj.add(rightKey, rightVal);
            }
        }
    }

    @SneakyThrows
    private void applyGitignore(String mergeTemplateResult, Path targetAbsoluteFilePath) {

        // Read in the entries we want to merge
        Map<String, String> ignoreEntryToComment = Maps.newHashMap();
        StringBuilder pendingCommentBuilder = new StringBuilder();
        for (String line : mergeTemplateResult.lines().toList()) {
            String lineTrimmed = line.trim();
            if (lineTrimmed.isEmpty()) {
                continue;
            } else if (line.trim().startsWith("#")) {
                pendingCommentBuilder.append(line).append("\n");
            } else {
                ignoreEntryToComment.put(line, pendingCommentBuilder.toString());
                pendingCommentBuilder.setLength(0);
            }
        }

        // Read in the original file, checking for which merge entries are already present
        // Read in target line by line
        StringBuilder gitignoreBuilder = new StringBuilder();
        try (var reader = Files.newBufferedReader(targetAbsoluteFilePath)) {
            // call reader.readLine
            String line;
            while ((line = reader.readLine()) != null) {
                gitignoreBuilder.append(line).append("\n");
                if (ignoreEntryToComment.remove(line.trim()) != null) {
                    log.debug("Skipping gitignore entry already present in file: {}", line);
                }
            }
        }

        // Write out the file back
        try (var writer = Files.newBufferedWriter(targetAbsoluteFilePath)) {
            writer.write(gitignoreBuilder.toString());
            for (Map.Entry<String, String> entry : ignoreEntryToComment.entrySet()) {
                writer.write("\n");
                writer.write(entry.getValue());
                writer.write(entry.getKey());
            }
            if (!ignoreEntryToComment.isEmpty()) {
                writer.write("\n");
            }
        }
    }
}
