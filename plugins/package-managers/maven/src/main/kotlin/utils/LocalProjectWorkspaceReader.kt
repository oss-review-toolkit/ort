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

import kotlin.collections.orEmpty

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository

/**
 * A special implementation of the [WorkspaceReader] interface that is used by Maven package managers to resolve
 * project artifacts. Projects are typically detected and handled specifically in concrete package manager
 * implementations. Therefore, this class allows customizing the access to project definition files via a resolver
 * function.
 */
internal class LocalProjectWorkspaceReader(
    /**
     * A function that can resolve an artifact identifier to a definition file if the artifact represents a local
     * project. The function should return *null* for artifacts that are no local projects. This reader invokes
     * the function to find out whether an artifact is a project and needs special treatment.
     */
    private val definitionFileResolveFun: (String) -> File?
) : WorkspaceReader {
    private val workspaceRepository = WorkspaceRepository("maven/remote-artifacts")

    override fun findArtifact(artifact: Artifact) =
        artifact.takeIf { it.extension == "pom" }?.let {
            definitionFileResolveFun(it.identifier())?.absoluteFile
        }

    override fun findVersions(artifact: Artifact) =
        // Avoid resolution of (SNAPSHOT) versions for local projects.
        definitionFileResolveFun(artifact.identifier())?.let { listOf(artifact.version) }.orEmpty()

    override fun getRepository() = workspaceRepository
}
