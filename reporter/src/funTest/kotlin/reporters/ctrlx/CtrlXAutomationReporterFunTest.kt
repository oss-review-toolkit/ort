/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters.ctrlx

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.ctrlx.CtrlXAutomationReporter.Companion.REPORT_FILENAME
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class CtrlXAutomationReporterFunTest : StringSpec({
    "The official sample file can be deserialized" {
        val fossInfoFile = File("src/funTest/assets/sample.fossinfo.json")
        val fossInfo = fossInfoFile.readValue<FossInfo>()

        fossInfo.components shouldNotBeNull {
            this should haveSize(8)
        }
    }

    "Generating a report works" {
        val outputDir = createTestTempDir()
        val reportFiles = CtrlXAutomationReporter().generateReport(ReporterInput(ORT_RESULT), outputDir)

        reportFiles.map { it.name } should containExactly(REPORT_FILENAME)
    }
})
