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

package org.ossreviewtoolkit.plugins.licensefactproviders.scancode

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.ints.beGreaterThan
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

@Tags("RequiresExternalTool")
class ScanCodeLicenseFunTest : WordSpec({
    "parseScanCodeLicenseDataFile()" should {
        "be able to parse all .LICENSE files in the detected ScanCode license data dir" {
            val licenseDataDir = checkNotNull(findScanCodeLicenseDataDir())
            val licenseDataFiles = licenseDataDir.listFiles { it.extension == "LICENSE" }

            licenseDataFiles.size should beGreaterThan(10)
            licenseDataFiles.forAll { file ->
                parseScanCodeLicenseDataFile(file) shouldNot beNull()
            }
        }
    }
})
