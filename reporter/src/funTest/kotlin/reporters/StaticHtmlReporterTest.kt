/*
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

package com.here.ort.reporter.reporters

import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.readValue
import com.here.ort.reporter.DefaultResolutionProvider

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.ByteArrayOutputStream
import java.io.File

import javax.xml.transform.TransformerFactory

class StaticHtmlReporterTest : WordSpec() {
    private val ortResult = File("src/funTest/assets/static-html-reporter-test-input.yml")
            .readValue<OrtResult>()

    init {
        "StaticHtmlReporter" should {
            "use the Apache Xalan TransformerFactory" {
                val transformer = TransformerFactory.newInstance().newTransformer()

                transformer.javaClass.name shouldBe "org.apache.xalan.transformer.TransformerIdentityImpl"
            }

            "successfully export to a static HTML page" {
                val actualReport = generateReport(ortResult)

                val expectedReport = File("src/funTest/assets/static-html-reporter-test-expected-output.html")
                        .readText()

                actualReport shouldBe expectedReport
            }
        }
    }

    private fun generateReport(ortResult: OrtResult) =
            ByteArrayOutputStream().also { outputStream ->
                StaticHtmlReporter().generateReport(
                        ortResult,
                        DefaultResolutionProvider(),
                        CopyrightGarbage(),
                        outputStream
                )
            }.toString("UTF-8")
}
