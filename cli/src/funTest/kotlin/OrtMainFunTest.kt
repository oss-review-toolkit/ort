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

package org.ossreviewtoolkit.cli

import com.github.ajalt.clikt.core.ProgramResult

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.OrtConfigurationWrapper
import org.ossreviewtoolkit.model.config.PackageCurationProviderConfiguration
import org.ossreviewtoolkit.model.config.REFERENCE_CONFIG_FILENAME
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter
import org.ossreviewtoolkit.utils.common.redirectStdout
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

/**
 * A test for the main entry point of the application.
 */
class OrtMainFunTest : StringSpec() {
    private lateinit var configFile: File
    private lateinit var outputDir: File

    override suspend fun beforeSpec(spec: Spec) {
        configFile = tempfile(suffix = ".yml")
        configFile.writeValue(
            OrtConfigurationWrapper(
                OrtConfiguration(
                    packageCurationProviders = listOf(
                        PackageCurationProviderConfiguration(
                            type = "File",
                            config = mapOf("path" to getAssetFile("gradle-curations.yml").path)
                        )
                    )
                )
            )
        )
    }

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = tempdir()
    }

    init {
        "Enabling only Gradle works" {
            val inputDir = tempdir()

            val stdout = runMain(
                "-c", configFile.path,
                "-P", "ort.analyzer.enabledPackageManagers=Gradle",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path
            )
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following 1 package manager(s) are enabled:") break
            }

            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\tGradle"
        }

        "Disabling only Gradle works" {
            val expectedPackageManagers = PackageManager.ENABLED_BY_DEFAULT.filterNot { it.type == "Gradle" }
            val markerLine = "The following ${expectedPackageManagers.size} package manager(s) are enabled:"
            val inputDir = tempdir()

            val stdout = runMain(
                "-c", configFile.path,
                "-P", "ort.analyzer.disabledPackageManagers=Gradle",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path
            )
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == markerLine) break
            }

            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\t${expectedPackageManagers.joinToString { it.type }}"
        }

        "Disabling a package manager overrides enabling it" {
            val inputDir = tempdir()

            val stdout = runMain(
                "-c", configFile.path,
                "-P", "ort.analyzer.enabledPackageManagers=Gradle,NPM",
                "-P", "ort.analyzer.disabledPackageManagers=Gradle",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path
            )
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following 1 package manager(s) are enabled:") break
            }

            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\tNPM"
        }

        "An Unmanaged project is created if no definition files are found" {
            val inputDir = tempdir()
            inputDir.resolve("test").writeText("test")

            @Suppress("IgnoredReturnValue")
            runMain(
                "-c", configFile.path,
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path
            )

            val ortResult = outputDir.resolve("analyzer-result.yml").readValue<OrtResult>()

            ortResult.analyzer shouldNotBeNull {
                result.projects should haveSize(1)
                result.projects.single().id.type shouldBe "Unmanaged"
            }
        }

        "No Unmanaged project is created if no definition files are found and Unmanaged is disabled" {
            val inputDir = tempdir()
            inputDir.resolve("test").writeText("test")

            @Suppress("IgnoredReturnValue")
            runMain(
                "-c", configFile.path,
                "-P", "ort.analyzer.enabledPackageManagers=Gradle,NPM",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path
            )

            val ortResult = outputDir.resolve("analyzer-result.yml").readValue<OrtResult>()

            ortResult.analyzer shouldNotBeNull {
                result.projects should beEmpty()
            }
        }

        "Output formats are deduplicated" {
            val inputDir = tempdir()

            val stdout = runMain(
                "-c", configFile.path,
                "-P", "ort.analyzer.enabledPackageManagers=Gradle",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path,
                "-f", "json,yaml,json"
            )
            val lines = stdout.filter { it.startsWith("Writing analyzer result to ") }

            lines.count() shouldBe 2
        }

        "Analyzer creates correct output" {
            val definitionFile = File(
                "../plugins/package-managers/gradle/src/funTest/assets/projects/synthetic/gradle/build.gradle"
            )
            val expectedResultFile = getAssetFile("gradle-all-dependencies-expected-result-with-curations.yml")
            val expectedResult = matchExpectedResult(expectedResultFile, definitionFile)

            @Suppress("IgnoredReturnValue")
            runMain(
                "-c", configFile.path,
                "-P", "ort.analyzer.enabledPackageManagers=Gradle",
                "analyze",
                "-i", definitionFile.parentFile.absolutePath,
                "-o", outputDir.path
            )

            val ortResult = outputDir.resolve("analyzer-result.yml").readValue<OrtResult>().withResolvedScopes()

            patchActualResult(ortResult.toYaml(), patchStartAndEndTime = true) shouldBe expectedResult
        }

        "EnvironmentVariableFilter is correctly initialized" {
            val referenceConfigFile = File("../model/src/main/resources/$REFERENCE_CONFIG_FILENAME").absolutePath

            @Suppress("IgnoredReturnValue")
            runMain(
                "-c", referenceConfigFile,
                "config"
            )

            EnvironmentVariableFilter.isAllowed("PASSPORT") shouldBe true
            EnvironmentVariableFilter.isAllowed("DB_PASS") shouldBe false
        }
    }

    private fun runMain(vararg args: String) =
        redirectStdout {
            @Suppress("SwallowedException")
            try {
                OrtMain().parse(args.asList())
            } catch (e: ProgramResult) {
                // Ignore exceptions that just propagate the program result.
            }
        }.lineSequence()
}
