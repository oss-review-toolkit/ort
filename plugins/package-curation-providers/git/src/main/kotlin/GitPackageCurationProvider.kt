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

package org.ossreviewtoolkit.plugins.packagecurationproviders.git

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.GitFactory
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.encodeOr
import org.ossreviewtoolkit.utils.common.fileSystemEncode
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

data class GitPackageCurationProviderConfig(
    /** The URL of the repository containing the curations. */
    val repositoryUrl: String,

    /** The optional revision to use. If not specified, the default branch is used. */
    val revision: String?,

    /** The path that contains the package curations. */
    @OrtPluginOption(defaultValue = "curations")
    val path: String
)

/**
 * A [PackageCurationProvider] that loads [PackageCuration]s from the configured Git repository.
 *
 * ### Path layout
 *
 * The provider requires that the curation files follow the same path layout as in the
 * [ort-config repository](https://github.com/oss-review-toolkit/ort-config#curations):
 *
 * * Files with curations for a specific package must be located at `[type]/[namespace]/[name].yml`, based on the
 *   identifier of the package. If a component of the identifier is empty, `_` is used as a placeholder. For example,
 *   for the package `NuGet::Azure.Core:1.2.0`, the curation file must be located at `NuGet/_/Azure.Core.yml`.
 * * Files with curations that match all packages within a namespace must be located at `[type]/[namespace]/_.yml`.
 *
 * Namespace-scoped curations are loaded before package-scoped curations, so that the latter can override the former.
 */
@OrtPlugin(
    displayName = "Git Repository",
    summary = "A package curation provider that loads package curations from a Git repository.",
    factory = PackageCurationProviderFactory::class
)
open class GitPackageCurationProvider(
    override val descriptor: PluginDescriptor = GitPackageCurationProviderFactory.descriptor,
    private val config: GitPackageCurationProviderConfig
) : PackageCurationProvider {
    init {
        require(config.repositoryUrl.isNotBlank()) { "The repository URL must not be blank." }
    }

    internal val repositoryDir by lazy {
        // Use a stable cache path to clone the repository to speed up subsequent runs.
        (ortDataDirectory / "cache" / "git-package-curation-provider" / config.repositoryUrl.fileSystemEncode()).also {
            updateRepository(it)
        }
    }

    private val curationsDir by lazy { repositoryDir / config.path }

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> =
        packages.flatMapTo(mutableSetOf()) { pkg -> getCurationsFor(pkg.id) }

    private fun getCurationsFor(pkgId: Identifier): List<PackageCuration> {
        // The Git repository has to follow path layout conventions, so curations can be looked up directly.
        val packageCurationsFile = curationsDir / pkgId.toCurationPath()

        // Also consider curations for all packages in a namespace.
        val namespaceCurationsFile = packageCurationsFile.resolveSibling("_.yml")

        // Return namespace-level curations before package-level curations to allow overriding the former.
        return listOf(namespaceCurationsFile, packageCurationsFile).filter { it.isFile }.flatMap { file ->
            runCatching {
                file.readValue<List<PackageCuration>>().filter { it.isApplicable(pkgId) }
            }.getOrElse {
                throw IOException("Failed parsing package curation from '${file.absolutePath}'.", it)
            }
        }
    }

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

/**
 * The path must be aligned with the
 * [conventions for the ort-config repository](https://github.com/oss-review-toolkit/ort-config#curations).
 */
fun Identifier.toCurationPath() = "${type.encodeOr("_")}/${namespace.encodeOr("_")}/${name.encodeOr("_")}.yml"
