/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packageconfigurationproviders.ortconfig

import java.io.File

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.dir.DirPackageConfigurationProvider
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

private const val ORT_CONFIG_REPOSITORY_BRANCH = "main"
private const val ORT_CONFIG_REPOSITORY_URL = "https://github.com/oss-review-toolkit/ort-config.git"
private const val PACKAGE_CONFIGURATIONS_DIR = "package-configurations"

class OrtConfigPackageConfigurationProviderFactory : PackageConfigurationProviderFactory<Unit> {
    override val type = "OrtConfig"

    override fun create(config: Unit): PackageConfigurationProvider = OrtConfigPackageConfigurationProvider()

    override fun parseConfig(config: Map<String, String>) = Unit
}

/**
 * A [PackageConfigurationProvider] that provides [PackageConfiguration]s from the
 * [ort-config repository](https://github.com/oss-review-toolkit/ort-config).
 */
class OrtConfigPackageConfigurationProvider : PackageConfigurationProvider {
    internal companion object : Logging

    private val configurationsDir by lazy {
        ortDataDirectory.resolve("ort-config").also {
            updateOrtConfig(it)
        }
    }

    private val provider by lazy {
        DirPackageConfigurationProvider(configurationsDir.resolve(PACKAGE_CONFIGURATIONS_DIR))
    }

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance) =
        provider.getPackageConfigurations(packageId, provenance)
}

private fun updateOrtConfig(dir: File) {
    dir.safeMkdirs()
    Git().apply {
        val workingTree = initWorkingTree(dir, VcsInfo.EMPTY.copy(type = VcsType.GIT, url = ORT_CONFIG_REPOSITORY_URL))
        val revision = updateWorkingTree(workingTree, ORT_CONFIG_REPOSITORY_BRANCH).getOrThrow()
        OrtConfigPackageConfigurationProvider.logger.info {
            "Successfully cloned $revision from $ORT_CONFIG_REPOSITORY_URL."
        }
    }
}
