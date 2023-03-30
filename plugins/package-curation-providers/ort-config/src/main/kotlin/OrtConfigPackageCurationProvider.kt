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

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.common.encodeOr
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

private const val ORT_CONFIG_REPOSITORY_BRANCH = "main"
private const val ORT_CONFIG_REPOSITORY_URL = "https://github.com/oss-review-toolkit/ort-config.git"

class OrtConfigPackageCurationProviderFactory : PackageCurationProviderFactory<Unit> {
    override val type = "OrtConfig"

    override fun create(config: Unit) = OrtConfigPackageCurationProvider()

    override fun parseConfig(config: Map<String, String>) = Unit
}

/**
 * A [PackageCurationProvider] that provides [PackageCuration]s loaded from the
 * [ort-config repository](https://github.com/oss-review-toolkit/ort-config).
 */
open class OrtConfigPackageCurationProvider : PackageCurationProvider {
    internal companion object : Logging

    private val curationsDir by lazy {
        ortDataDirectory.resolve("ort-config").also {
            updateOrtConfig(it)
        }
    }

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> =
        packages.flatMapTo(mutableSetOf()) { pkg -> getCurationsFor(pkg.id) }

    private fun getCurationsFor(pkgId: Identifier): List<PackageCuration> {
        val file = curationsDir.resolve("curations").resolve(pkgId.toCurationPath())
        return if (file.isFile) {
            runCatching {
                file.readValue<List<PackageCuration>>().filter { it.isApplicable(pkgId) }
            }.getOrElse {
                throw IOException("Failed parsing package curation from '${file.absolutePath}'.", it)
            }
        } else {
            emptyList()
        }
    }
}

/**
 * The path must be aligned with the
 * [conventions for the ort-config repository](https://github.com/oss-review-toolkit/ort-config#curations).
 */
private fun Identifier.toCurationPath() =
    "${type.encodeOr("_")}/${namespace.encodeOr("_")}/${name.encodeOr("_")}.yml"

private fun updateOrtConfig(dir: File) {
    dir.safeMkdirs()
    Git().apply {
        val workingTree = initWorkingTree(dir, VcsInfo.EMPTY.copy(type = VcsType.GIT, url = ORT_CONFIG_REPOSITORY_URL))
        val revision = updateWorkingTree(workingTree, ORT_CONFIG_REPOSITORY_BRANCH).getOrThrow()
        OrtConfigPackageCurationProvider.logger.info {
            "Successfully cloned $revision from $ORT_CONFIG_REPOSITORY_URL."
        }
    }
}
