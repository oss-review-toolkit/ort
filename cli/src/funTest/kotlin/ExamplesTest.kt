/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File
import java.io.IOException

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.licenses.LicenseConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.reporter.reporters.AbstractNoticeReporter

class ExamplesTest : StringSpec() {
    private val examplesDir = File("../docs/examples")

    init {
        "ort.yml examples are parsable" {
            examplesDir.listFiles { file ->
                file.name.endsWith(".ort.yml")
            }!!.forEach { file ->
                withClue(file.name) {
                    shouldNotThrow<IOException> {
                        file.readValue<RepositoryConfiguration>()
                    }
                }
            }
        }

        "copyright-garbage.yml can be deserialized" {
            shouldNotThrow<IOException> {
                examplesDir.resolve("copyright-garbage.yml").readValue<CopyrightGarbage>()
            }
        }

        "curations.yml can be deserialized" {
            shouldNotThrow<IOException> {
                examplesDir.resolve("curations.yml").readValue<List<PackageCuration>>()
            }
        }

        "licenses.yml can be deserialized" {
            shouldNotThrow<IOException> {
                examplesDir.resolve("licenses.yml").readValue<LicenseConfiguration>()
            }
        }

        "notice-pre-processor.kts can be compiled" {
            val model = AbstractNoticeReporter.NoticeReportModel(
                headers = emptyList(),
                headerWithoutLicenses = "",
                headerWithLicenses = "",
                findings = emptyMap(),
                footers = emptyList()
            )

            val preProcessor = AbstractNoticeReporter.PreProcessor(
                ortResult = OrtResult.EMPTY,
                model = model,
                copyrightGarbage = CopyrightGarbage(),
                licenseConfiguration = LicenseConfiguration(),
                packageConfigurationProvider = SimplePackageConfigurationProvider()
            )

            val script = examplesDir.resolve("notice-pre-processor.kts").readText()

            preProcessor.checkSyntax(script) shouldBe true

            // TODO: It should also be verified that the script works as expected.
        }

        "resolutions.yml can be deserialized" {
            shouldNotThrow<IOException> {
                examplesDir.resolve("resolutions.yml").readValue<Resolutions>()
            }
        }

        "rules.kts can be compiled" {
            val evaluator = Evaluator(
                ortResult = OrtResult.EMPTY,
                packageConfigurationProvider = SimplePackageConfigurationProvider(),
                licenseConfiguration = LicenseConfiguration()
            )

            val script = examplesDir.resolve("rules.kts").readText()

            evaluator.checkSyntax(script) shouldBe true

            // TODO: It should also be verified that the script works as expected.
        }
    }
}
