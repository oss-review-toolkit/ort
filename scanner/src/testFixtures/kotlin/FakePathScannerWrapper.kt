/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import java.io.File

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * An implementation of [PathScannerWrapper] that creates scan results with one license finding for each file.
 */
class FakePathScannerWrapper : PathScannerWrapper {
    override val descriptor = PluginDescriptor(id = "fake", displayName = "fake", description = "")
    override val version = "1.0.0"
    override val configuration = "config"

    override val matcher = ScannerMatcher.create(details)
    override val readFromStorage = true
    override val writeToStorage = true

    override fun scanPath(path: File, context: ScanContext): ScanSummary {
        val licenseFindings = path.walk().filter { it.isFile }.mapTo(mutableSetOf()) { file ->
            LicenseFinding("Apache-2.0", TextLocation(file.relativeTo(path).path, 1, 2))
        }

        return ScanSummary.EMPTY.copy(licenseFindings = licenseFindings)
    }
}
