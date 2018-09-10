/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration

import java.io.File

/**
 * A fake [PackageManager] for projects that do not use any of the known package managers.
 */
class Unmanaged(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Unmanaged>() {
        override val globsForDefinitionFiles = emptyList<String>()

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Unmanaged(analyzerConfig, repoConfig)
    }

    /**
     * Return a [ProjectAnalyzerResult] for the [Project] contained in the [definitionFile] directory, but does not
     * perform any dependency resolution.
     *
     * @param definitionFile The directory containing the unmanaged project.
     */
    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val project = Project(
                id = Identifier(
                        provider = toString(),
                        namespace = "",
                        name = definitionFile.name,
                        version = ""
                ),
                definitionFilePath = "",
                declaredLicenses = sortedSetOf(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = VersionControlSystem.getCloneInfo(definitionFile),
                homepageUrl = "",
                scopes = sortedSetOf()
        )

        return ProjectAnalyzerResult(project, sortedSetOf())
    }
}
