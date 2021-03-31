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

package org.ossreviewtoolkit.analyzer.integration

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.managers.Bower
import org.ossreviewtoolkit.analyzer.managers.Npm
import org.ossreviewtoolkit.analyzer.managers.Yarn
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class PolymerIntegrationFunTest : AbstractIntegrationSpec() {
    override val pkg = Package(
        id = Identifier(
            type = "Bower",
            namespace = "",
            name = "polymer",
            version = "2.4.0"
        ),
        declaredLicenses = sortedSetOf(),
        description = "",
        homepageUrl = "",
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/Polymer/polymer.git",
            revision = "v2.4.0"
        )
    )

    private fun findDownloadedFiles(vararg filenames: String) =
        outputDir.walk().filterTo(mutableListOf()) { it.name in filenames }

    override val expectedManagedFiles by lazy {
        val bowerJsonFiles = findDownloadedFiles("bower.json")
        val packageJsonFiles = findDownloadedFiles("package.json")

        mapOf(
            Bower.Factory() as PackageManagerFactory to bowerJsonFiles,
            Npm.Factory() as PackageManagerFactory to packageJsonFiles,
            Yarn.Factory() as PackageManagerFactory to packageJsonFiles
        )
    }

    override val managedFilesForTest by lazy {
        mapOf(
            Bower.Factory() as PackageManagerFactory to listOf(outputDir.resolve("bower.json")),
            Npm.Factory() as PackageManagerFactory to listOf(outputDir.resolve("package.json"))
        )
    }
}
