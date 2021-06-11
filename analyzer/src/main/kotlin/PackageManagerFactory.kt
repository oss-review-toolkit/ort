/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.util.ServiceLoader

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

/**
 * A common interface for use with [ServiceLoader] that all [AbstractPackageManagerFactory] classes need to implement.
 */
interface PackageManagerFactory {
    /**
     * The name to use to refer to the package manager.
     */
    val managerName: String

    /**
     * The glob matchers for all definition files.
     */
    val matchersForDefinitionFiles: List<PathMatcher>

    /**
     * Create a [PackageManager] for analyzing the [analysisRoot] directory using the specified [analyzerConfig] and
     * [repoConfig].
     */
    fun create(
        analysisRoot: File,
        analyzerConfig: AnalyzerConfiguration,
        repoConfig: RepositoryConfiguration
    ): PackageManager
}

/**
 * A generic factory class for a [PackageManager].
 */
abstract class AbstractPackageManagerFactory<out T : PackageManager>(
    override val managerName: String
) : PackageManagerFactory {
    /**
     * The prioritized list of glob patterns of definition files supported by this package manager. Only all matches of
     * the first glob having any matches are considered.
     */
    abstract val globsForDefinitionFiles: List<String>

    override val matchersForDefinitionFiles by lazy {
        globsForDefinitionFiles.map {
            FileSystems.getDefault().getPathMatcher("glob:**/$it")
        }
    }

    abstract override fun create(
        analysisRoot: File,
        analyzerConfig: AnalyzerConfiguration,
        repoConfig: RepositoryConfiguration
    ): T

    /**
     * Return the package manager's name here to allow Clikt to display something meaningful when listing the
     * package managers which are enabled by default via their factories.
     */
    override fun toString() = managerName
}
