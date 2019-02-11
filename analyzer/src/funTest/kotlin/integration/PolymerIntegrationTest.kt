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

package com.here.ort.analyzer.integration

import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.analyzer.managers.Bower
import com.here.ort.analyzer.managers.NPM
import com.here.ort.analyzer.managers.Yarn
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo

import java.io.File

class PolymerIntegrationTest : AbstractIntegrationSpec() {
    override val pkg: Package = Package(
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
                    type = "Git",
                    url = "https://github.com/Polymer/polymer.git",
                    revision = "v2.4.0"
            )
    )

    private fun findDownloadedFiles(vararg filenames: String) =
            downloadResult.downloadDirectory.walkTopDown().filter { it.name in filenames }.toList()

    override val expectedManagedFiles by lazy {
        val bowerJsonFiles = findDownloadedFiles("bower.json")
        val packageJsonFiles = findDownloadedFiles("package.json")

        mapOf(
                Bower.Factory() as PackageManagerFactory to bowerJsonFiles,
                NPM.Factory() as PackageManagerFactory to packageJsonFiles,
                Yarn.Factory() as PackageManagerFactory to packageJsonFiles
        )
    }

    override val managedFilesForTest by lazy {
        mapOf(
                Bower.Factory() as PackageManagerFactory to
                        listOf(File(downloadResult.downloadDirectory, "bower.json")),
                NPM.Factory() as PackageManagerFactory to
                        listOf(File(downloadResult.downloadDirectory, "package.json"))
        )
    }
}
