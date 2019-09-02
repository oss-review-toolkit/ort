/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import com.here.ort.utils.test.readOrtResult

import io.kotlintest.matchers.beEmpty
import io.kotlintest.should
import io.kotlintest.specs.WordSpec

import org.cyclonedx.BomParser

class CycloneDxReporterTest : WordSpec({
    "A generated BOM" should {
        "be valid" {
            val bomParser = BomParser()
            val bomFile = createTempFile().also {
                CycloneDxReporter().generateReport(
                    it.outputStream(),
                    readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")
                )

                it.deleteOnExit()
            }

            bomParser.validate(bomFile) should beEmpty()
        }
    }
})
