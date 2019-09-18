/*
 * Copyright (C) 2019 HERE Europe B.V.
 * Copyright (C) 2019 Verifa Oy.
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

package com.here.ort.analyzer.managers

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.matchers.beEmpty
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class ConanTest : StringSpec() {
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
            val packageFile = File(projectsDirTxt, "conanfile.txt")
            val vcsPath = vcsDirTxt.getPathToRoot(projectsDirTxt)
            val expectedResult = patchExpectedResult(
                File(projectsDirTxt.parentFile, "conan-expected-output-txt.yml"),
                definitionFilePath = "$vcsPath/conanfile.txt",
                path = vcsPath,
                revision = vcsRevisionTxt,
                url = normalizeVcsUrl(vcsUrlTxt)
            )

            val result = createConan().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors should beEmpty()
            patchActualResult(yamlMapper.writeValueAsString(result)) shouldBe expectedResult
        }

       "Project dependencies are detected correctly for conanfile.py" {
            val packageFile = File(projectsDirPy, "conanfile.py")
            val vcsPath = vcsDirPy.getPathToRoot(projectsDirPy)
            val expectedResult = patchExpectedResult(
                File(projectsDirPy.parentFile, "conan-expected-output-py.yml"),
                definitionFilePath = "$vcsPath/conanfile.py",
                path = vcsPath,
                revision = vcsRevisionPy,
                url = normalizeVcsUrl(vcsUrlPy)
            )

            val result = createConan().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors should beEmpty()
            patchActualResult(yamlMapper.writeValueAsString(result)) shouldBe expectedResult
        }
    }

    private fun createConan() =
        Conan("Conan", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
