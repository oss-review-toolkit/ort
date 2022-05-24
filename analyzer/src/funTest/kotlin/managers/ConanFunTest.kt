/*
 * Copyright (C) 2019 HERE Europe B.V.
 * Copyright (C) 2019 Verifa Oy.
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
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class ConanFunTest : StringSpec() {
    private val projectsDirTxt = File("src/funTest/assets/projects/synthetic/conan-txt").absoluteFile
    private val vcsDirTxt = VersionControlSystem.forDirectory(projectsDirTxt)!!
    private val vcsRevisionTxt = vcsDirTxt.getRevision()
    private val vcsUrlTxt = vcsDirTxt.getRemoteUrl()

    private val projectsDirPy = File("src/funTest/assets/projects/synthetic/conan-py").absoluteFile
    private val vcsDirPy = VersionControlSystem.forDirectory(projectsDirPy)!!
    private val vcsRevisionPy = vcsDirPy.getRevision()
    private val vcsUrlPy = vcsDirPy.getRemoteUrl()

    init {
        "Project dependencies are detected correctly for conanfile.txt" {
            val packageFile = projectsDirTxt.resolve("conanfile.txt")
            val vcsPath = vcsDirTxt.getPathToRoot(projectsDirTxt)
            val expectedResult = patchExpectedResult(
                projectsDirTxt.parentFile.resolve("conan-expected-output-txt.yml"),
                definitionFilePath = "$vcsPath/conanfile.txt",
                path = vcsPath,
                revision = vcsRevisionTxt,
                url = normalizeVcsUrl(vcsUrlTxt)
            )

            val result = createConan().resolveSingleProject(packageFile)

            patchActualResult(result.toYaml()) shouldBe expectedResult
        }

        "Project dependencies are detected correctly for conanfile.py" {
            val packageFile = projectsDirPy.resolve("conanfile.py")
            val vcsPath = vcsDirPy.getPathToRoot(projectsDirPy)
            val expectedResult = patchExpectedResult(
                projectsDirPy.parentFile.resolve("conan-expected-output-py.yml"),
                definitionFilePath = "$vcsPath/conanfile.py",
                path = vcsPath,
                revision = vcsRevisionPy,
                url = normalizeVcsUrl(vcsUrlPy)
            )

            val result = createConan().resolveSingleProject(packageFile)

            patchActualResult(result.toYaml()) shouldBe expectedResult
        }
    }

    private fun createConan() =
        Conan("Conan", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
