/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.scanners.fossologynomossa

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class NomossaResultExtensionsTest : WordSpec({
    "byteOffsetsToLineNumbers" should {
        "map byte offsets to line numbers correctly for ASCII content" {
            val content = "line one\nline two\nline three\n".toByteArray()

            byteOffsetsToLineNumbers(content, 9, 17) shouldBe (2 to 2)
        }

        "map byte offsets to line numbers correctly when a multi-byte character precedes the match" {
            // The first line contains a multi-byte UTF-8 character ('ä' encodes to 2 bytes), so a character-based
            // offset calculation would misplace the line boundary that follows it.
            val content = "// ä comment\nlicensed under the Apache License, Version 2.0\n".toByteArray()
            val licenseStart = content.toString(Charsets.UTF_8).indexOf("licensed").let {
                content.toString(Charsets.UTF_8).substring(0, it).toByteArray(Charsets.UTF_8).size
            }

            val licenseEnd = licenseStart + "licensed".toByteArray(Charsets.UTF_8).size

            byteOffsetsToLineNumbers(content, licenseStart, licenseEnd) shouldBe (2 to 2)
        }
    }
})
