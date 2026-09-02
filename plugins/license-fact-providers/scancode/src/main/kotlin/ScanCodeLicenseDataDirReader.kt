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

import java.io.File

internal class ScanCodeLicenseDataDirReader(val licenseDataDir: File) {
    private fun getLicenseTextFile(licenseOrExceptionId: String): File? {
        val filename = if (licenseOrExceptionId == "LicenseRef-scancode-unlimited-link-exception-lgpl") {
            // Work around for https://github.com/oss-review-toolkit/ort/issues/12369.
            "unlimited-linking-exception-lgpl.LICENSE"
        } else {
            "${licenseOrExceptionId.removePrefix("LicenseRef-scancode-").lowercase()}.LICENSE"
        }

        return licenseDataDir.resolve(filename).takeIf { it.isFile && it.isNotBlank }
    }

    fun getLicenseText(licenseOrExceptionId: String): String? =
        getLicenseTextFile(licenseOrExceptionId)?.useLines { lines ->
            lines.skipYamlFrontMatter().joinToString("\n").trimEnd()
        }

    fun hasLicenseText(licenseOrExceptionId: String): Boolean = getLicenseTextFile(licenseOrExceptionId) != null
}

private val File.isNotBlank: Boolean
    get() = useLines { lines -> lines.skipYamlFrontMatter().any { line -> line.any { !it.isWhitespace() } } }

private fun Sequence<String>.skipYamlFrontMatter(): Sequence<String> {
    var inFrontMatter = false

    return filterIndexed { index, line ->
        if (index == 0 && line == "---") {
            inFrontMatter = true
            false
        } else if (inFrontMatter) {
            if (line == "---") inFrontMatter = false
            false
        } else {
            true
        }
    }.dropWhile {
        it.isBlank()
    }
}
