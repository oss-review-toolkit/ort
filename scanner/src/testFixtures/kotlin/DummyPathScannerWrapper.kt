/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

class DummyPathScannerWrapper(id: String = "Dummy") : PathScannerWrapper {
    override val descriptor = PluginDescriptor(id = id, displayName = id, description = "")
    override val version = "1.0.0"
    override val configuration = ""

    override val matcher = null
    override val readFromStorage = false
    override val writeToStorage = false

    override fun scanPath(path: File, context: ScanContext): ScanSummary {
        val relevantFiles = path.walk()
            .onEnter { it.name !in VCS_DIRECTORIES }
            .filter { it.isFile }

        val licenseFindings = relevantFiles.mapTo(mutableSetOf()) { file ->
            LicenseFinding(
                license = SpdxConstants.NOASSERTION,
                location = TextLocation(file.relativeTo(path).invariantSeparatorsPath, TextLocation.UNKNOWN_LINE)
            )
        }

        return ScanSummary.EMPTY.copy(
            licenseFindings = licenseFindings
        )
    }
}
