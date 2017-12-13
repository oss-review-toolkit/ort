/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import com.here.ort.analyzer.managers.PhpComposer
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.utils.Expensive
import com.here.ort.utils.yamlMapper
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.should

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

private val projectDir = File("src/funTest/assets/projects/synthetic/php-composer")

class PhpComposerTest : StringSpec() {
    init {
        "Php composer recognises project" {
            val result = PackageManager.findManagedFiles(projectDir, listOf(PhpComposer))
            result[PhpComposer]?.isEmpty() shouldBe false
        }

        "Project dependencies are detected correctly" {
            val packageFile = File(projectDir, "composer.json")
            val result = PhpComposer.create().resolveDependencies(projectDir, listOf(packageFile))[packageFile]
            result shouldNotBe null
            result!!.project.scopes shouldNotBe null
            result.project.scopes.size shouldBe 2
            result.packages.size should beGreaterThan(0)
            result.hasErrors() shouldBe false
        }

        "Drupal dependencies are detected correctly" {
            val outputDir = createTempDir()
            val pkg = Package(
                    packageManager = "PhpComposer",
                    namespace = "",
                    name = "Drupal",
                    version = "",
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo(
                            "Git",
                            "https://github.com/drupal/drupal",
                            "4a765491d80d1bcb11e542ffafccf10aef05b853",
                            ""
                    ))

            val downloadedDir = com.here.ort.downloader.Main.download(pkg, outputDir)
            val analyzerResultsDir = File(outputDir, "analyzer_results")

            Main.main(arrayOf(
                    "-m", "PhpComposer",
                    "-i", downloadedDir.absolutePath,
                    "-o", analyzerResultsDir.absolutePath))

            analyzerResultsDir.walkTopDown().filter { it.extension == "yml" }.forEach { actualResultsFile ->
                val result = yamlMapper.readValue(actualResultsFile, AnalyzerResult::class.java)
                result?.hasErrors() shouldBe false
            }
            outputDir.deleteRecursively()
        }.config(tags = setOf(Expensive))
    }
}
