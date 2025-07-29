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

import com.github.ajalt.clikt.testing.test

import io.kotest.assertions.withClue
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.ProviderPluginConfiguration
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.extractResource
import org.ossreviewtoolkit.utils.ort.ORT_REFERENCE_CONFIG_FILENAME

/**
 * A test for the main entry point of the application.
 */
class OrtMainFunTest : StringSpec() {
    private lateinit var configFile: File
    private lateinit var outputDir: File

    override suspend fun beforeSpec(spec: Spec) {
        configFile = tempfile(suffix = ".yml")

        val curationsFile = tempdir() / "gradle-curations.yml"
        extractResource("/gradle-curations.yml", curationsFile)

        val writer = configFile.mapper().writerFor(OrtConfiguration::class.java).withRootName("ort")
        val config = OrtConfiguration(
            packageCurationProviders = listOf(
                ProviderPluginConfiguration(
                    type = "File",
                    options = mapOf("path" to curationsFile.path)
                )
            )
        )

        writer.writeValue(configFile, config)
    }

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = tempdir()
    }

    init {
        "Enabling only Gradle works" {
            val inputDir = tempdir()

            val result = OrtMain().test(
                "-c", configFile.path,
                "-P", "ort.analyzer.enabledPackageManagers=Gradle",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path
            )

            val stdout = result.stdout.lineSequence()
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following 1 package manager(s) are enabled:") break
            }

            withClue(result.stderr) {
                iterator.hasNext() shouldBe true
                iterator.next().trim() shouldBe "Gradle"
            }
        }

        "Disabling only Gradle works" {
            val expectedPackageManagers = PackageManagerFactory.ALL.values.filterNot { it.descriptor.id == "Gradle" }
            val markerLine = "The following ${expectedPackageManagers.size} package manager(s) are enabled:"
            val inputDir = tempdir()

            val result = OrtMain().test(
                "-c", configFile.path,
                "-P", "ort.analyzer.disabledPackageManagers=Gradle",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path
            )

            val stdout = result.stdout.lineSequence()
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == markerLine) break
            }

            withClue(result.stderr) {
                iterator.hasNext() shouldBe true
                iterator.next().trim() shouldBe expectedPackageManagers.joinToString { it.descriptor.displayName }
            }
        }

        "Disabling a package manager overrides enabling it" {
            val inputDir = tempdir()

            val result = OrtMain().test(
                "-c", configFile.path,
                "-P", "ort.analyzer.enabledPackageManagers=Gradle,NPM",
                "-P", "ort.analyzer.disabledPackageManagers=Gradle",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path
            )

            val stdout = result.stdout.lineSequence()
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following 1 package manager(s) are enabled:") break
            }

            withClue(result.stderr) {
                iterator.hasNext() shouldBe true
                iterator.next().trim() shouldBe "NPM"
            }
        }

        "An Unmanaged project is created if no definition files are found" {
            val inputDir = tempdir()
            inputDir.resolve("test").writeText("test")

            OrtMain().test(
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

            OrtMain().test(
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

            val result = OrtMain().test(
                "-c", configFile.path,
                "-P", "ort.analyzer.enabledPackageManagers=Gradle",
                "analyze",
                "-i", inputDir.path,
                "-o", outputDir.path,
                "-f", "json,yaml,json"
            )

            val stdout = result.stdout.lineSequence()
            val lines = stdout.filter { it.startsWith("Wrote analyzer result to ") }

            withClue(result.stderr) {
                lines.count() shouldBe 2
            }
        }

        "EnvironmentVariableFilter is correctly initialized" {
            val referenceConfigFile = File("../model/src/main/resources/$ORT_REFERENCE_CONFIG_FILENAME").absolutePath

            OrtMain().test(
                "-c", referenceConfigFile,
                "config"
            )

            EnvironmentVariableFilter.isAllowed("PASSPORT") shouldBe true
            EnvironmentVariableFilter.isAllowed("DB_PASS") shouldBe false
        }
    }
}
