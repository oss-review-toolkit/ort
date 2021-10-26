/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class DotNetFunTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/dotnet").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()
    private val packageFile = projectDir.resolve("subProjectTest/test.csproj")

    init {
        "Definition file is correctly read" {
            val reader = DotNetPackageFileReader()
            val result = reader.getPackageReferences(packageFile)

            result should containExactly(
                Identifier(type = "NuGet", namespace = "", name = "jQuery", version = "3.3.1"),
                Identifier(type = "NuGet", namespace = "", name = "WebGrease", version = "1.5.2"),
                Identifier(type = "NuGet", namespace = "", name = "foobar", version = "1.2.3")
            )
        }

        "Project dependencies are detected correctly" {
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                File(
                    projectDir.parentFile,
                    "dotnet-expected-output.yml"
                ),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = "$vcsPath/subProjectTest"
            )
            val result = createDotNet().resolveSingleProject(packageFile)

            patchActualResult(result.toYaml()) shouldBe expectedResult
        }

        "Project metadata is correctly extracted from a .nuspec file" {
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                File(
                    projectDir.parentFile,
                    "dotnet-expected-output-with-nuspec.yml"
                ),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = "$vcsPath/subProjectTestWithNuspec"
            )
            val result = createDotNet()
                .resolveSingleProject(projectDir.resolve("subProjectTestWithNuspec/test.csproj"))

            patchActualResult(result.toYaml()) shouldBe expectedResult
        }
    }

    private fun createDotNet() =
        DotNet("DotNet", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
