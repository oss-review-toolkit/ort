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

class GoModTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/gomod").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Project dependencies are detected correctly" {
            val definitionFile = File(projectDir, "go.mod")
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                File(projectDir.parentFile, "gomod-expected-output.yml"),
                definitionFilePath = "$vcsPath/go.mod",
                path = vcsPath,
                revision = vcsRevision,
                url = normalizeVcsUrl(vcsUrl)
            )

            val result = createGoMod().resolveDependencies(listOf(definitionFile))[definitionFile]

            result shouldNotBe null
            result!!.issues should beEmpty()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }

    private fun createGoMod() =
        GoMod("GoMod", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
