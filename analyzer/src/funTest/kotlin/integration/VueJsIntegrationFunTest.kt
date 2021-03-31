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
import org.ossreviewtoolkit.analyzer.managers.Npm
import org.ossreviewtoolkit.analyzer.managers.Yarn
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class VueJsIntegrationFunTest : AbstractIntegrationSpec() {
    override val pkg = Package(
        id = Identifier(
            type = "NPM",
            namespace = "",
            name = "Vue.js",
            version = ""
        ),
        declaredLicenses = sortedSetOf(),
        description = "",
        homepageUrl = "",
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/vuejs/vue.git",
            revision = "v2.5.11"
        )
    )

    override val expectedManagedFiles by lazy {
        mapOf(
            Npm.Factory() as PackageManagerFactory to listOf(
                outputDir.resolve("package.json"),
                outputDir.resolve("packages/vue-server-renderer/package.json"),
                outputDir.resolve("packages/vue-template-compiler/package.json"),
                outputDir.resolve("packages/weex-template-compiler/package.json"),
                outputDir.resolve("packages/weex-vue-framework/package.json")
            ),
            Yarn.Factory() as PackageManagerFactory to listOf(
                outputDir.resolve("package.json"),
                outputDir.resolve("packages/vue-server-renderer/package.json"),
                outputDir.resolve("packages/vue-template-compiler/package.json"),
                outputDir.resolve("packages/weex-template-compiler/package.json"),
                outputDir.resolve("packages/weex-vue-framework/package.json")
            )
        )
    }

    override val managedFilesForTest by lazy {
        mapOf(
            Npm.Factory() as PackageManagerFactory to listOf(outputDir.resolve("package.json"))
        )
    }
}
