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

import org.apache.logging.log4j.kotlin.logger

internal class ScanCodeLicenseDataDirReader(
    val licenseDataDir: File,
    val filterPredicate: (ScanCodeLicense) -> Boolean = { true }
) {
    /** Associates license or exception IDs with license data files which contain non-blank license texts. */
    private val licenseDataFileForLicenseOrExceptionId: Map<String, File> by lazy {
        buildMap {
            licenseDataDir.listFiles().filter { it.extension == "LICENSE" }.forEach { file ->
                val licenseData = parseScanCodeLicenseDataFile(file)

                if (licenseData == null) {
                    logger.warn {
                        "Could not parse ScanCode license data file: '${file.absolutePath}'."
                    }

                    return@forEach
                }

                if (!filterPredicate(licenseData)) {
                    return@forEach
                }

                licenseData.getAllLicenseOrExceptionIds().forEach { id ->
                    if (id in this) {
                        logger.warn {
                            "Not associating '$id' with '${file.name}', as it is already associated with " +
                                "'${getValue(id).name}'."
                        }
                    } else {
                        this[id] = file
                    }
                }
            }
        }
    }

    fun getLicense(licenseOrExceptionId: String): ScanCodeLicense? {
        val file = licenseDataFileForLicenseOrExceptionId[licenseOrExceptionId] ?: return null
        return checkNotNull(parseScanCodeLicenseDataFile(file))
    }

    fun hasLicense(licenseOrExceptionId: String): Boolean =
        licenseOrExceptionId in licenseDataFileForLicenseOrExceptionId
}

private fun ScanCodeLicense.getAllLicenseOrExceptionIds(): Set<String> =
    buildSet {
        if (key.isNotBlank()) {
            add("LicenseRef-scancode-$key")
        }

        if (spdxLicenseKey != null) {
            add(spdxLicenseKey)
        }

        addAll(otherSpdxLicenseKeys)
    }
