/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.plugins.packagemanagers.maven.utils

import java.io.File

import org.apache.maven.project.ProjectBuilder

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.WorkspaceReader

/**
 * A specialized [WorkspaceReader] implementation used when building a Maven project that prevents unnecessary
 * downloads of binary artifacts.
 *
 * When building a Maven project from a POM using Maven's [ProjectBuilder] API clients have no control over the
 * downloads of dependencies: If dependencies are to be resolved, all the artifacts of these dependencies are
 * automatically downloaded. For the purpose of just constructing the dependency tree, this is not needed and only
 * costs time and bandwidth.
 *
 * Unfortunately, there is no official API to prevent the download of dependencies. However, Maven can be tricked to
 * believe that the artifacts are already present on the local disk - then the download is skipped. This is what
 * this implementation does. It reports that all binary artifacts are available locally, and only treats POMs
 * correctly, as they may be required for the dependency analysis.
 */
internal class SkipBinaryDownloadsWorkspaceReader(
    /** The real workspace reader to delegate to. */
    private val delegate: WorkspaceReader
) : WorkspaceReader by delegate {
    /**
     * Locate the given artifact on the local disk. This implementation does a correct location only for POM files;
     * for all other artifacts it returns a non-null file. Note: For the purpose of analyzing the project's
     * dependencies the artifact files are never accessed. Therefore, the concrete file returned here does not
     * actually matter; it just has to be non-null to indicate that the artifact is present locally.
     */
    override fun findArtifact(artifact: Artifact): File? {
        return if (artifact.extension == "pom") {
            delegate.findArtifact(artifact)
        } else {
            File(artifact.artifactId)
        }
    }
}
