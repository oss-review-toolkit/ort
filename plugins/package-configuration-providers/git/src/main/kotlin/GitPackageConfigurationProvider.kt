/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packageconfigurationproviders.git

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.dir.DirPackageConfigurationProvider
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.GitFactory
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.fileSystemEncode
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

data class GitPackageConfigurationProviderConfig(
    /** The URL of the repository containing the package configurations. */
    val repositoryUrl: String,

    /** The optional revision to use. If not specified, the default branch is used. */
    val revision: String?,

    /** The path relative to the repository root that contains the package configurations. */
    @OrtPluginOption(defaultValue = "package-configurations")
    val path: String
)

/**
 * A [PackageConfigurationProvider] that loads [PackageConfiguration]s from the configured Git repository.
 */
@OrtPlugin(
    displayName = "Git Repository",
    summary = "A package configuration provider that loads package configurations from a Git repository.",
    factory = PackageConfigurationProviderFactory::class
)
open class GitPackageConfigurationProvider(
    override val descriptor: PluginDescriptor = GitPackageConfigurationProviderFactory.descriptor,
    private val config: GitPackageConfigurationProviderConfig
) : PackageConfigurationProvider {
    init {
        require(config.repositoryUrl.isNotBlank()) { "The repository URL must not be blank." }
    }

    internal val repositoryDir by lazy {
        // Use the same path as the `GitPackageCurationProvider` as they are likely configured together.
        (ortDataDirectory / "cache" / "git-package-curation-provider" / config.repositoryUrl.fileSystemEncode()).also {
            updateRepository(it)
        }
    }

    private val provider by lazy {
        DirPackageConfigurationProvider(repositoryDir / config.path)
    }

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance) =
        provider.getPackageConfigurations(packageId, provenance)

    private fun updateRepository(dir: File) {
        val vcsInfo = VcsInfo.EMPTY.copy(type = VcsType.GIT, url = config.repositoryUrl)
        dir.safeMkdirs()

        GitFactory.create().apply {
            val workingTree = initWorkingTree(dir, vcsInfo)
            val revision = config.revision ?: getDefaultBranchName(config.repositoryUrl)
            val clonedRevision = updateWorkingTree(workingTree, revision).getOrThrow()

            logger.info {
                buildString {
                    append("Successfully cloned revision $clonedRevision ")
                    if (revision != clonedRevision) append("(from $revision) ")
                    append("of ${config.repositoryUrl}.")
                }
            }
        }
    }
}
