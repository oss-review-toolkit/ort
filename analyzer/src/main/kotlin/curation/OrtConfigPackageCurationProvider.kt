/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.curation

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageCurationProvider
import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.encodeOr
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

private const val ORT_CONFIG_REPOSITORY_BRANCH = "main"
private const val ORT_CONFIG_REPOSITORY_URL = "https://github.com/oss-review-toolkit/ort-config.git"

/**
 * A [PackageCurationProvider] that provides [PackageCuration]s loaded from the
 * [ort-config repository](https://github.com/oss-review-toolkit/ort-config).
 */
open class OrtConfigPackageCurationProvider : PackageCurationProvider {
    private val curationsDir by lazy {
        ortDataDirectory.resolve("ort-config").also {
            updateOrtConfig(it)
        }
    }

    override fun getCurationsFor(pkgIds: Collection<Identifier>) =
        pkgIds.mapNotNull { pkgId ->
            getCurationsFor(pkgId).takeIf { it.isNotEmpty() }?.let { pkgId to it }
        }.toMap()

    private fun getCurationsFor(pkgId: Identifier): List<PackageCuration> {
        val file = curationsDir.resolve("curations").resolve(pkgId.toCurationPath())
        return if (file.isFile) {
            runCatching {
                yamlMapper.readValue<List<PackageCuration>>(file).filter { it.isApplicable(pkgId) }
            }.onFailure {
                log.warn { "Failed parsing package curation from '${file.absolutePath}'." }
            }.getOrThrow()
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
        val workingTree =
            initWorkingTree(dir, VcsInfo(VcsType.GIT, url = ORT_CONFIG_REPOSITORY_URL, ORT_CONFIG_REPOSITORY_BRANCH))
        updateWorkingTree(workingTree, ORT_CONFIG_REPOSITORY_BRANCH)
    }
}
