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

package com.here.ort.analyzer.integration

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.analyzer.managers.Bundler
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo

import java.io.File

class SimpleFormIntegrationTest : AbstractIntegrationSpec() {
    override val pkg: Package = Package(
            id = Identifier(
                    provider = "Bundler",
                    namespace = "",
                    name = "Simple Form",
                    version = ""
            ),
            declaredLicenses = sortedSetOf("MIT"),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(
                    type = "Git",
                    url = "https://github.com/plataformatec/simple_form.git",
                    revision = "516e31ce8f3eb32c5bac9d2a4902fba0783363fb",
                    path = ""
            )
    )

    override val expectedDefinitionFiles by lazy {
        val downloadDir = downloadResult.downloadDirectory
        mapOf(
                Bundler as PackageManagerFactory<PackageManager> to listOf(
                        File(downloadDir, "Gemfile")
                )
        )
    }
}
