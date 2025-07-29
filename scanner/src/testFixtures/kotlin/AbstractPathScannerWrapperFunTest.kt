/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner

import io.kotest.core.Tag
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.file.aFile
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.test.readResource

abstract class AbstractPathScannerWrapperFunTest(testTags: Set<Tag> = emptySet()) : StringSpec() {
    // This is loosely based on the patterns from
    // https://github.com/licensee/licensee/blob/6c0f803/lib/licensee/project_files/license_file.rb#L6-L43.
    private val commonlyDetectedFiles = listOf("LICENSE", "LICENCE", "COPYING")
    private val scanContext = ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)

    private lateinit var inputDir: File

    protected abstract val scanner: PathScannerWrapper
    protected abstract val expectedFileLicenses: List<LicenseFinding>
    protected abstract val expectedDirectoryLicenses: List<LicenseFinding>

    override suspend fun beforeSpec(spec: Spec) {
        inputDir = tempdir()

        // Create variants of the Apache-2.0 license text in a temporary directory, so we have something to operate on.
        val licenseText = readResource("/LICENSE")
        commonlyDetectedFiles.forEach {
            inputDir.resolve(it).writeText(licenseText.replace("license", it))
        }
    }

    init {
        "Scanning a single file succeeds".config(tags = testTags) {
            val result = scanner.scanPath(inputDir / "LICENSE", scanContext)
            val findings = result.licenseFindings.map { it.copy(location = it.location.withRelativePath(inputDir)) }

            findings shouldContainExactlyInAnyOrder expectedFileLicenses
            findings.forAll {
                inputDir / it.location.path shouldBe aFile()
            }
        }

        "Scanning a directory succeeds".config(tags = testTags) {
            val result = scanner.scanPath(inputDir, scanContext)
            val findings = result.licenseFindings.map { it.copy(location = it.location.withRelativePath(inputDir)) }

            findings shouldContainExactlyInAnyOrder expectedDirectoryLicenses
            findings.forAll {
                inputDir / it.location.path shouldBe aFile()
            }
        }
    }
}
