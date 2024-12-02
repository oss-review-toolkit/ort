/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.testing.test

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.helper.HelperMain
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.test.getAssetFile

class CreateAnalyzerResultFromPackageListCommandFunTest : WordSpec({
    "The command" should {
        "generate the expected analyzer result file" {
            val inputFile = getAssetFile("package-list.yml")
            val outputFile = tempdir().resolve("analyzer-result.yml")
            val expectedOutputFile = getAssetFile("create-analyzer-result-from-pkg-list-expected-output.yml")
            val ortConfigFile = createOrtConfig()

            HelperMain().test(
                "create-analyzer-result-from-package-list",
                "--config",
                ortConfigFile.absolutePath,
                "--package-list-file",
                inputFile.absolutePath,
                "--ort-file",
                outputFile.absolutePath
            )

            outputFile.readValue<OrtResult>().patchAnalyzerResult() shouldBe
                expectedOutputFile.readValue<OrtResult>().patchAnalyzerResult()
        }
    }
})

private fun createOrtConfig(): File {
    val config = OrtConfiguration().copy(packageCurationProviders = emptyList())

    val ortConfigFile = createOrtTempFile(suffix = "config.yml").apply {
        writeText(mapOf("ort" to config).toYaml())
    }

    return ortConfigFile
}

private fun OrtResult.patchAnalyzerResult(): OrtResult =
    copy(
        analyzer = analyzer?.copy(environment = Environment())
    )
