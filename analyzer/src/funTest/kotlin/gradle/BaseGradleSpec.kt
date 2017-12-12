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

package com.here.ort.analyzer.integration


import com.here.ort.analyzer.managers.Gradle
import com.here.ort.analyzer.Main as AnalyzerMain
import com.here.ort.downloader.Main as DownloaderMain
import com.here.ort.model.Package
import com.here.ort.utils.Expensive

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import io.kotlintest.specs.StringSpec

import java.io.File

abstract class BaseGradleSpec : StringSpec() {
    abstract val pkg: Package
    abstract val expectedResultsDir: String

    // Map here expected results files locations if for some reason, they cannot be stored in identical directories
    // as in src (ex. file paths get to long on Windows)
    protected open val expectedResultsDirsMap = mapOf<String, File>()
    protected val outputDir = createTempDir()

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        spec()
        outputDir.deleteRecursively()
    }

    override val oneInstancePerTest: Boolean
        get() = false

    init {
        "analyzer produces ABCD files for all .gradle files" {
            //FIXME:  Analyzer crashes on JAVA 9 with project below Gradle 4.3
            //        (Gradle issue: https://github.com/gradle/gradle/issues/3317)
            val downloadedDir = DownloaderMain.download(pkg, outputDir)
            val analyzerResultsDir = File(outputDir, "analyzer_results")
            AnalyzerMain.main(arrayOf(
                    "-i", downloadedDir.absolutePath,
                    "-o", analyzerResultsDir.absolutePath)
            )

            val sourceGradleProjectFiles = mutableListOf<File>()

            downloadedDir.walkTopDown().filter {
                it.isDirectory
            }.forEach { dir ->
                val matches = Gradle.matchersForDefinitionFiles.mapNotNull { glob ->
                    dir.listFiles().find { file ->
                        glob.matches(file.toPath())
                    }
                }

                if (matches.isNotEmpty()) {
                    sourceGradleProjectFiles.add(matches.first())
                }
            }

            val expectedResult = sourceGradleProjectFiles.map {
                val abcdFileDir = it.absolutePath.substringBeforeLast(File.separator).replace(
                        oldValue = downloadedDir.absolutePath,
                        newValue = analyzerResultsDir.absolutePath,
                        ignoreCase = true)
                "$abcdFileDir${File.separator}${it.nameWithoutExtension}-gradle-dependencies.yml"
            }

            val generatedResultFiles = analyzerResultsDir.walkTopDown().filter { it.extension == "yml" }.map {
                it.absolutePath
            }

            generatedResultFiles.joinToString("\n") shouldBe expectedResult.joinToString("\n")
        }.config(tags = setOf(Expensive))

        "analyzer results for all .gradle files match expected" {
            expectedResultsDir shouldNotBe ""

            val analyzerResultsDir = File(outputDir, "analyzer_results/")
            val testRows = analyzerResultsDir.walkTopDown().asIterable().filter { file ->
                file.extension == "yml"
            }.map {
                val fileExpectedResultPath = expectedResultsDir + it.path.substringBeforeLast(
                        File.separator).substringAfterLast("analyzer_results").replace("\\",
                        "/") + "/" + it.name //keep as unix paths
                row(it, expectedResultsDirsMap.getOrDefault(fileExpectedResultPath, File(fileExpectedResultPath)))
            }

            val gradleTable = table(headers("analyzerOutputFile", "expectedResultFile"), *testRows.toTypedArray())

            forAll(gradleTable) { analyzerOutputFile, expectedResultFile ->
                val analyzerResults = analyzerOutputFile.readText()
                        // vcs:
                        .replaceFirst("revision: \"[^\"]+\"".toRegex(), "revision: \"\"")
                        // vcs_processed:
                        .replaceFirst("revision: \"[^\"]+\"".toRegex(), "revision: \"\"")
                val expectedResults = expectedResultFile.readText()
                        // vcs:
                        .replaceFirst("revision: \"[^\"]+\"".toRegex(), "revision: \"\"")
                        // vcs_processed:
                        .replaceFirst("revision: \"[^\"]+\"".toRegex(), "revision: \"\"")
                analyzerResults shouldBe expectedResults
            }
        }.config(tags = setOf(Expensive))
    }
}
