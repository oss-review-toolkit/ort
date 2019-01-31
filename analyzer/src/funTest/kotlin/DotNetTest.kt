/*
 * Copyright (C) 2019 Bosch Software Innovations
 * Based on:
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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.DotNet
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.matchers.beEmpty
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File

class DotNetTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/dotnet")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir.absoluteFile)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()
    private val packageFile = File(projectDir, "subProjectTest/test.csproj")


    init {
        "Project dependencies are detected correctly" {
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(File(projectDir.parentFile,
                    "dotnet-expected-output.yml"),
                    definitionFilePath = "$vcsPath/subProjectTest/test.csproj",
                    path = "$vcsPath/subProjectTest",
                    revision = vcsRevision,
                    url = normalizeVcsUrl(vcsUrl))

            val result = DotNet("DotNet", DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
                .resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors should beEmpty()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Definition File is correctly mapped" {
            val mapper = XmlMapper().registerKotlinModule()

            val result: List<DotNet.Companion.ItemGroup> = mapper.readValue(packageFile)

            result shouldNotBe null
            result.size shouldBe 4
            result[1].packageReference?.size shouldBe 2
        }
    }
}
