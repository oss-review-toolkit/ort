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

import com.here.ort.model.Environment
import com.here.ort.model.OrtResult
import com.here.ort.reporter.DefaultResolutionProvider
import com.here.ort.utils.test.patchExpectedResult
import com.here.ort.utils.test.readOrtResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.ByteArrayOutputStream
import java.io.File

import javax.xml.transform.TransformerFactory

private fun generateReport(ortResult: OrtResult) =
    ByteArrayOutputStream().also { outputStream ->
        val resolutionProvider = DefaultResolutionProvider()
        ortResult.repository.config.resolutions?.let { resolutionProvider.add(it) }

        StaticHtmlReporter().generateReport(
            outputStream,
            ortResult,
            resolutionProvider
        )
    }.toString("UTF-8")

class StaticHtmlReporterTest : WordSpec({
    "StaticHtmlReporter" should {
        "use the Apache Xalan TransformerFactory" {
            val transformer = TransformerFactory.newInstance().newTransformer()

            transformer.javaClass.name shouldBe "org.apache.xalan.transformer.TransformerIdentityImpl"
        }

        "successfully export to a static HTML page" {
            val timeStampPattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")
            val actualReport = generateReport(ortResult).replace(timeStampPattern, "<REPLACE_TIMESTAMP>")

            val expectedReport = patchExpectedResult(
                File("src/funTest/assets/static-html-reporter-test-expected-output.html"),
                "<REPLACE_ORT_VERSION>" to Environment().ortVersion
            )

            actualReport shouldBe expectedReport
        }
    }
})
