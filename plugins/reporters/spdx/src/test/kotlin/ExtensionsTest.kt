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

package org.ossreviewtoolkit.plugins.reporters.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.mockk

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxFile

class ExtensionsTest : WordSpec({
    "toSpdxPackage" should {
        "set filesAnalyzed to true if there is a package verification code and files are available" {
            val id = Identifier("Maven:pkg1-grp:pkg1:0.0.1")
            val pkg = requireNotNull(ORT_RESULT.getPackage(id)?.metadata)
            val spdxFile = SpdxFile(
                spdxId = "SPDXRef-Pkg1-testFile",
                filename = "testFile",
                checksums = emptyList(),
                licenseConcluded = "MIT"
            )
            val files = listOf(spdxFile)

            val spdxPkg = pkg.toSpdxPackage(
                type = SpdxPackageType.VCS_PACKAGE,
                licenseInfoResolver = mockk(relaxed = true),
                ortResult = ORT_RESULT,
                files = files
            )

            spdxPkg.filesAnalyzed shouldBe true
            spdxPkg.hasFiles shouldContainExactly listOf(spdxFile.spdxId)
            spdxPkg.packageVerificationCode shouldNot beNull()
        }

        "set filesAnalyzed to false and no verification code if there are no files available" {
            val id = Identifier("Maven:pkg1-grp:pkg1:0.0.1")
            val pkg = requireNotNull(ORT_RESULT.getPackage(id)?.metadata)

            val spdxPkg = pkg.toSpdxPackage(
                type = SpdxPackageType.VCS_PACKAGE,
                licenseInfoResolver = mockk(relaxed = true),
                ortResult = ORT_RESULT
            )

            spdxPkg.filesAnalyzed shouldBe false
            spdxPkg.hasFiles should beEmpty()
            spdxPkg.packageVerificationCode should beNull()
        }
    }
})
