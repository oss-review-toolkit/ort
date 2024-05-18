/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bitbake

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.Git
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class BitBakeFunTest : WordSpec({
    "BitBake" should {
        "get the version correctly" {
            val bitBake = create("BitBake") as BitBake

            val version = bitBake.getBitBakeVersion(tempdir())

            version shouldMatch "\\d+\\.\\d+\\.\\d+"
        }
    }

    "Analyzing recipes from Poky" should {
        val projectDir = tempdir()
        val pokyVcsInfo = VcsInfo(VcsType.GIT, "https://git.yoctoproject.org/poky", "kirkstone-4.0.17")

        Git().run {
            val workingTree = initWorkingTree(projectDir, pokyVcsInfo)
            updateWorkingTree(workingTree, pokyVcsInfo.revision)
        } shouldBeSuccess pokyVcsInfo.revision

        "create an SPDX file for the 'quilt-native' package" {
            val recipeFileName = "quilt-native_0.67.bb"
            val result = Analyzer(AnalyzerConfiguration()).run {
                val fileInfo = findManagedFiles(projectDir)
                val singleFileInfo = fileInfo.copy(
                    managedFiles = fileInfo.managedFiles.map { (packageManager, definitionsFiles) ->
                        packageManager to definitionsFiles.filter { it.name == recipeFileName }
                    }.toMap()
                )
                analyze(singleFileInfo)
            }

            result.analyzer?.result shouldNotBeNull {
                projects shouldHaveSize 1

                with(projects.single()) {
                    id shouldBe Identifier("BitBake:OpenEmbedded ():quilt-native:0.67")
                    declaredLicenses shouldBe setOf("GPL-2.0-only")
                    homepageUrl shouldBe "http://savannah.nongnu.org/projects/quilt/"
                    scopes should beEmpty()
                }
            }
        }

        "create a SPDX files for the 'xmlto' package".config(tags = setOf(ExpensiveTag)) {
            val recipeFileName = "xmlto_0.0.28.bb"
            val result = Analyzer(AnalyzerConfiguration()).run {
                val fileInfo = findManagedFiles(projectDir)
                val singleFileInfo = fileInfo.copy(
                    managedFiles = fileInfo.managedFiles.map { (packageManager, definitionsFiles) ->
                        packageManager to definitionsFiles.filter { it.name == recipeFileName }
                    }.toMap()
                )
                analyze(singleFileInfo)
            }

            result.analyzer?.result shouldNotBeNull {
                projects shouldHaveSize 90
            }
        }
    }
})
