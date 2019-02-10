/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.log

import java.io.File

/**
 * A fake [PackageManager] for projects that do not use any of the known package managers.
 */
class Unmanaged(name: String, analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(name, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Unmanaged>("Unmanaged") {
        override val globsForDefinitionFiles = emptyList<String>()

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Unmanaged(managerName, analyzerConfig, repoConfig)
    }

    /**
     * Return a [ProjectAnalyzerResult] for the [Project] contained in the [definitionFile] directory, but does not
     * perform any dependency resolution.
     *
     * @param definitionFile The directory containing the unmanaged project.
     */
    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val vcsInfo = VersionControlSystem.getCloneInfo(definitionFile)

        val id = when {
            vcsInfo == VcsInfo.EMPTY -> {
                // This seems to be an analysis of a local directory that is not under version control, i.e. that is not
                // a VCS working tree. In this case we have no change to get a version.
                val projectDir = definitionFile.absoluteFile

                log.warn {
                    "Analysis of local directory '$projectDir' which is not under version control will produce" +
                            "non-cacheable results as no version for the cache key can be determined."
                }

                Identifier(
                        type = managerName,
                        namespace = "",
                        name = projectDir.name,
                        version = ""
                )
            }

            vcsInfo.type == "GitRepo" -> {
                // For GitRepo looking at the URL and revision only is not enough, we also need to take the used
                // manifest into account.
                Identifier(
                        type = managerName,
                        namespace = vcsInfo.path.substringBeforeLast('/'),
                        name = vcsInfo.path.substringAfterLast('/').removeSuffix(".xml"),
                        version = vcsInfo.revision
                )
            }

            else -> {
                // For all non-GitRepo VCSes derive the name from the VCS URL.
                Identifier(
                        type = managerName,
                        namespace = "",
                        name = vcsInfo.url.split('/').last().removeSuffix(".git"),
                        version = vcsInfo.revision
                )
            }
        }

        val project = Project(
                id = id,
                definitionFilePath = "",
                declaredLicenses = sortedSetOf(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = vcsInfo,
                homepageUrl = "",
                scopes = sortedSetOf()
        )

        return ProjectAnalyzerResult(project, sortedSetOf())
    }
}
