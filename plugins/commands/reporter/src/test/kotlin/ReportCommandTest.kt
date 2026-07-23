/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.commands.reporter

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.testing.test

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.string.shouldContain

import java.io.File

import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_DIR_ENV_NAME

class ReportCommandTest : StringSpec({
    "License choices from the license choices file are merged before reporting" {
        val ortFile = File("src/test/resources/test-ort-result.yml")
        val outputDir = tempdir()

        val licenseChoicesFile = tempfile(suffix = ".yml").apply {
            writeText(
                """
                repository_license_choices:
                - given: "Apache-2.0 OR MIT"
                  choice: "MIT"
                """.trimIndent()
            )
        }

        val args = listOf(
            "--ort-file", ortFile.path,
            "--output-dir", outputDir.path,
            "--report-formats", "EvaluatedModel",
            "--license-choices-file", licenseChoicesFile.path
        )

        ReportCommand().context { obj = OrtConfiguration() }
            .test(args, envvars = mapOf(ORT_CONFIG_DIR_ENV_NAME to tempdir().path))

        // The EvaluatedModel report serializes the (merged) repository configuration, so the license
        // choice provided via the global file must appear in its output.
        val evaluatedModelFile = outputDir.walk().first { it.isFile && it.name.startsWith("evaluated-model") }
        evaluatedModelFile.readText() shouldContain "Apache-2.0 OR MIT"
    }
})
