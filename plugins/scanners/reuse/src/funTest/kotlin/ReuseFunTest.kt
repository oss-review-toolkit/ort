/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.scanners.reuse

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.utils.test.getAssetFile

/**
 * A functional test for the [Reuse] scanner.
 *
 * This test requires the `reuse` command to be available in the PATH.
 * It uses a fixture project with various file types to verify that the scanner
 * correctly extracts license and copyright information.
 */
@Tags("RequiresExternalTool")
class ReuseFunTest : StringSpec({
    val scanContext = ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)
    val projectDir = getAssetFile("projects/reuse-project")

    "Scanner version should be available" {
        val scanner = ReuseFactory.create()

        scanner.version shouldNotBe ""
    }

    "Scanning a REUSE project should return expected license findings" {
        val scanner = ReuseFactory.create()

        val result = scanner.scanPath(projectDir, scanContext)

        // Files with license headers should have license findings
        // - valid_license.py: MIT
        // - bs_license.py: LicenseRef-BS
        // - no_copyright.py: MIT
        // - with_copyright.py: MIT
        // - unlicensed.py: no license
        // LICENSES/*.txt files are reported based on their filename

        result.licenseFindings shouldContainExactlyInAnyOrder listOf(
            LicenseFinding("MIT", TextLocation("LICENSES/MIT.txt", TextLocation.UNKNOWN_LINE)),
            LicenseFinding("LicenseRef-BS", TextLocation("LICENSES/LicenseRef-BS.txt", TextLocation.UNKNOWN_LINE)),
            LicenseFinding("MIT", TextLocation("src/valid_license.py", TextLocation.UNKNOWN_LINE)),
            LicenseFinding("LicenseRef-BS", TextLocation("src/bs_license.py", TextLocation.UNKNOWN_LINE)),
            LicenseFinding("MIT", TextLocation("src/no_copyright.py", TextLocation.UNKNOWN_LINE)),
            LicenseFinding("MIT", TextLocation("src/with_copyright.py", TextLocation.UNKNOWN_LINE))
        )
    }

    "Scanning a REUSE project should return expected copyright findings" {
        val scanner = ReuseFactory.create()

        val result = scanner.scanPath(projectDir, scanContext)

        // Files with copyright statements:
        // - valid_license.py: "2024 Test Author <test@example.com>"
        // - bs_license.py: "2024 BS Corp"
        // - with_copyright.py: "2023 First Author" and "2024 Second Author"
        // - no_copyright.py: no copyright
        // - unlicensed.py: no copyright

        result.copyrightFindings shouldContainExactlyInAnyOrder listOf(
            CopyrightFinding(
                "SPDX-FileCopyrightText: 2024 Test Author <test@example.com>",
                TextLocation("src/valid_license.py", TextLocation.UNKNOWN_LINE)
            ),
            CopyrightFinding(
                "SPDX-FileCopyrightText: 2024 BS Corp",
                TextLocation("src/bs_license.py", TextLocation.UNKNOWN_LINE)
            ),
            CopyrightFinding(
                "SPDX-FileCopyrightText: 2023 First Author",
                TextLocation("src/with_copyright.py", TextLocation.UNKNOWN_LINE)
            ),
            CopyrightFinding(
                "SPDX-FileCopyrightText: 2024 Second Author",
                TextLocation("src/with_copyright.py", TextLocation.UNKNOWN_LINE)
            )
        )
    }

    "Unlicensed files should not appear in license findings" {
        val scanner = ReuseFactory.create()

        val result = scanner.scanPath(projectDir, scanContext)

        // The unlicensed.py file should not have any license findings
        val unlicensedFindings = result.licenseFindings.filter {
            it.location.path == "src/unlicensed.py"
        }

        unlicensedFindings shouldBe emptySet()
    }

    "Files without copyright should not appear in copyright findings" {
        val scanner = ReuseFactory.create()

        val result = scanner.scanPath(projectDir, scanContext)

        // The no_copyright.py and unlicensed.py files should not have copyright findings
        val noCopyrightFindings = result.copyrightFindings.filter {
            it.location.path in listOf("src/no_copyright.py", "src/unlicensed.py")
        }

        noCopyrightFindings shouldBe emptySet()
    }
})
