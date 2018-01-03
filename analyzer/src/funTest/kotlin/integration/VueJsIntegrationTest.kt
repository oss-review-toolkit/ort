/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
import com.here.ort.analyzer.managers.NPM
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo

import java.io.File

class VueJsIntegrationTest : AbstractIntegrationSpec() {

    override val pkg: Package = Package(
            packageManager = "NPM",
            namespace = "",
            name = "Vue.js",
            version = "",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(
                    provider = "Git",
                    url = "https://github.com/vuejs/vue.git",
                    revision = "v2.5.10",
                    path = ""
            )
    )

    override val expectedDefinitionFiles by lazy {
        mapOf(
                NPM as PackageManagerFactory<PackageManager> to listOf(
                        File(downloadDir, "package.json"),
                        File(downloadDir, "packages/vue-server-renderer/package.json"),
                        File(downloadDir, "packages/vue-template-compiler/package.json"),
                        File(downloadDir, "packages/weex-template-compiler/package.json"),
                        File(downloadDir, "packages/weex-vue-framework/package.json")
                )
        )
    }

    override val definitionFilesForTest by lazy {
        mapOf(NPM as PackageManagerFactory<PackageManager> to listOf(File(downloadDir, "package.json")))
    }
}
