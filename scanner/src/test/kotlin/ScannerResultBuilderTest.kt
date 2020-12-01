/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.nio.file.Files
import java.time.Instant

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively

class ScannerResultBuilderTest : WordSpec({
    "StreamingScannerResultBuilder" should {
        "handle null fields in the result from the analyzer" {
            val outputFile = Files.createTempFile("scan-result", ".yml").toFile()
            outputFile.deleteOnExit()
            val analyzerResult = OrtResult(
                Repository(
                    VcsInfo(VcsType.CVS, "someUri", "someRevision")
                )
            )

            StreamingScannerResultBuilder(outputFile).use { builder ->
                builder.initFromAnalyzerResult(analyzerResult)
                builder.complete(
                    Instant.now(),
                    Instant.now(),
                    Environment(),
                    ScannerConfiguration(),
                    AccessStatistics(),
                    emptyMap()
                )
            }

            val result = outputFile.readText()
            result shouldContain "analyzer: null"
            result shouldContain "advisor: null"
            result shouldContain "evaluator: null"
        }

        "create the output folder if it does not exist yet" {
            val outputDir = createTempDirectory("$ORT_NAME-scanner-builder").toFile()
            val outputFile = File(outputDir, "sub/folder/scan-result.yml")

            StreamingScannerResultBuilder(outputFile).use { builder ->
                builder.initFromAnalyzerResult(OrtResult.EMPTY)
                builder.complete(
                    Instant.now(),
                    Instant.now(),
                    Environment(),
                    ScannerConfiguration(),
                    AccessStatistics(),
                    emptyMap()
                )
            }

            outputFile.isFile shouldBe true

            outputDir.safeDeleteRecursively(force = true)
        }
    }
})
