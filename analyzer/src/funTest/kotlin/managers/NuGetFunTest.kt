/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.analyzer.managers.utils.NuGetDependency
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetSupport.Companion.OPTION_DIRECT_DEPENDENCIES_ONLY
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.toYaml

class NuGetFunTest : StringSpec({
    val projectDir = getAssetFile("projects/synthetic/nuget")
    val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    val vcsUrl = vcsDir.getRemoteUrl()
    val vcsRevision = vcsDir.getRevision()
    val definitionFile = projectDir.resolve("packages.config")

    "Definition file is correctly read" {
        val reader = NuGetPackageFileReader()
        val result = reader.getDependencies(definitionFile)

        result should containExactly(
            NuGetDependency(name = "System.Globalization", version = "4.3.0", targetFramework = "netcoreapp3.1"),
            NuGetDependency(name = "System.Threading", version = "4.0.11", targetFramework = "netcoreapp3.1"),
            NuGetDependency(
                name = "System.Threading.Tasks.Extensions",
                version = "4.5.4",
                targetFramework = "net45"
            ),
            NuGetDependency(
                name = "WebGrease",
                version = "1.5.2",
                targetFramework = "netcoreapp3.1",
                developmentDependency = true
            ),
            NuGetDependency(name = "foobar", version = "1.2.3", targetFramework = "netcoreapp3.1")
        )
    }

    "Project dependencies are detected correctly" {
        val vcsPath = vcsDir.getPathToRoot(projectDir)
        val expectedResult = patchExpectedResult(
            projectDir.resolveSibling("nuget-expected-output.yml"),
            url = normalizeVcsUrl(vcsUrl),
            revision = vcsRevision,
            path = vcsPath
        )
        val result = createNuGet().resolveSingleProject(definitionFile)

        patchActualResult(result.toYaml()) shouldBe expectedResult
    }

    "Direct project dependencies are detected correctly" {
        val vcsPath = vcsDir.getPathToRoot(projectDir)
        val expectedResult = patchExpectedResult(
            projectDir.resolveSibling("nuget-direct-dependencies-only-expected-output.yml"),
            url = normalizeVcsUrl(vcsUrl),
            revision = vcsRevision,
            path = vcsPath
        )
        val result = createNuGet(directDependenciesOnly = true).resolveSingleProject(definitionFile)

        patchActualResult(result.toYaml()) shouldBe expectedResult
    }
})

private fun createNuGet(directDependenciesOnly: Boolean = false) =
    NuGet(
        "NuGet",
        USER_DIR,
        AnalyzerConfiguration(
            packageManagers = mapOf(
                "NuGet" to PackageManagerConfiguration(
                    options = mapOf(
                        OPTION_DIRECT_DEPENDENCIES_ONLY to "$directDependenciesOnly"
                    )
                )
            )
        ),
        RepositoryConfiguration()
    )
