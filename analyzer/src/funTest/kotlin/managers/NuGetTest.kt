/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

import io.kotlintest.matchers.beEmpty
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class NuGetTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/nuget").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()
    private val packageFile = File(projectDir, "packages.config")

    init {
        "Definition file is correctly mapped" {
            val mapper = NuGetPackageReferenceMapper()
            val result = mapper.mapPackageReferences(packageFile)

            result.size shouldBe 2
        }

        "Project dependencies are detected correctly" {
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                File(
                    projectDir.parentFile,
                    "nuget-expected-output.yml"
                ),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = vcsPath
            )
            val result = createNuGet().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.issues should beEmpty()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }

    private fun createNuGet() =
        NuGet("NuGet", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
