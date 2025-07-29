/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.ortconfig

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.encodeOr
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

private const val ORT_CONFIG_REPOSITORY_BRANCH = "main"
private const val ORT_CONFIG_REPOSITORY_URL = "https://github.com/oss-review-toolkit/ort-config.git"

/**
 * A [PackageCurationProvider] that provides [PackageCuration]s loaded from the
 * [ort-config repository](https://github.com/oss-review-toolkit/ort-config).
 */
@OrtPlugin(
    id = "ORTConfig",
    displayName = "ORT Config Repository",
    description = "A package curation provider that loads package curations from the ort-config repository.",
    factory = PackageCurationProviderFactory::class
)
open class OrtConfigPackageCurationProvider(
    override val descriptor: PluginDescriptor = OrtConfigPackageCurationProviderFactory.descriptor
) : PackageCurationProvider {
    private val curationsDir by lazy {
        ortDataDirectory.resolve("ort-config").also {
            updateOrtConfig(it)
        }
    }

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> =
        packages.flatMapTo(mutableSetOf()) { pkg -> getCurationsFor(pkg.id) }

    private fun getCurationsFor(pkgId: Identifier): List<PackageCuration> {
        // The ORT config repository follows path layout conventions, so curations can be looked up directly.
        val packageCurationsFile = curationsDir / "curations" / pkgId.toCurationPath()

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
}

/**
 * The path must be aligned with the
 * [conventions for the ort-config repository](https://github.com/oss-review-toolkit/ort-config#curations).
 */
fun Identifier.toCurationPath() = "${type.encodeOr("_")}/${namespace.encodeOr("_")}/${name.encodeOr("_")}.yml"

private fun updateOrtConfig(dir: File) {
    val vcsInfo = VcsInfo.EMPTY.copy(type = VcsType.GIT, url = ORT_CONFIG_REPOSITORY_URL)
    val vcs = checkNotNull(VersionControlSystem.forType(vcsInfo.type)) {
        "No applicable VersionControlSystem implementation found for ${vcsInfo.type}."
    }

    dir.safeMkdirs()

    vcs.apply {
        val workingTree = initWorkingTree(dir, vcsInfo)
        val revision = updateWorkingTree(workingTree, ORT_CONFIG_REPOSITORY_BRANCH).getOrThrow()
        logger.info {
            "Successfully cloned $revision from $ORT_CONFIG_REPOSITORY_URL."
        }
    }
}
