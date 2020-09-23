/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

class LicenseFilenamePatternsTest : WordSpec({
    "globFileInDirectoryOrAncestors" should {
        "return the expected globs given a non-root directory" {
            LicenseFilenamePatterns.getFileGlobsForDirectoryAndAncestors(
                directory = "/some/path",
                filenamePatterns = listOf("LICENSE", "PATENTS")
            ) should containExactly(
                "/LICENSE",
                "/PATENTS",
                "/some/LICENSE",
                "/some/PATENTS",
                "/some/path/**/LICENSE",
                "/some/path/**/PATENTS"
            )
        }

        "return the expected globs given the root directory" {
            LicenseFilenamePatterns.getFileGlobsForDirectoryAndAncestors(
                directory = "/",
                filenamePatterns = listOf("LICENSE", "PATENTS")
            ) should containExactly(
                "**/LICENSE",
                "**/PATENTS"
            )
        }
    }

    "getLicenseFileGlobsForDirectory" should {
        "return a list containing the expected patterns for the LICENSE file" {
            LicenseFilenamePatterns.getLicenseFileGlobsForDirectory("/dir") should containAll(
                "/LICENSE*",
                "/dir/**/LICENSE*"
            )
        }
    }
})
