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

import com.google.common.collect.ImmutableMap;
import io.dataspray.core.definition.model.DataFormat;
import io.dataspray.core.definition.model.Processor;
import io.dataspray.common.VersionUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.UserConfig;

@ApplicationScoped
public class ContextBuilder {

    @Inject
    ContextUtil contextUtil;
    @Inject
    VersionUtil versionUtil;

    public DatasprayContext createForFilename(
            DatasprayContext parentContext,
            Project project) {
        return new DatasprayContext(ImmutableMap.<String, Object>builder()
                .putAll(parentContext.getData())
                .build());
    }

    public DatasprayContext createForTemplates(
            Project project) {
        return new DatasprayContext(createProjectBase(project)
                .build());
    }

    public DatasprayContext createForDataFormat(
            Project project,
            DataFormat dataFormat) {
        return new DatasprayContext(createProjectBase(project)
                .put("dataFormat", dataFormat)
                .build());
    }

    public DatasprayContext createForProcessor(
            Project project,
            Processor processor) {
        return new DatasprayContext(createProjectBase(project)
                .put("processor", processor)
                .build());
    }

    private ImmutableMap.Builder<String, Object> createProjectBase(
            Project project) {
        return ImmutableMap.<String, Object>builder()
                .put("datasprayVersion", versionUtil.getVersion())
                .put("definition", project.getDefinition())
                .put("git", createGit(project.getGit()))
                .put("util", contextUtil);
    }

    private ImmutableMap<String, Object> createGit(Git git) {
        UserConfig userConfig = git.getRepository().getConfig().get(UserConfig.KEY);
        return ImmutableMap.of(
                "authorName", userConfig.getAuthorName(),
                "authorEmail", userConfig.getAuthorEmail());
    }
}
