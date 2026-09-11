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

import org.ossreviewtoolkit.utils.common.collectMessages

/**
 * Reads the licenses texts from the given license data directory. If multiple license data files correspond to the same
 * license identifier, then only the first file is used.
 */
internal class ScanCodeLicenseDataDirReader(
    val licenseDataDir: File,
    filterPredicate: (ScanCodeLicense) -> Boolean = { true }
) {
    init {
        require(licenseDataDir.isDirectory) {
            "The license data directory '${licenseDataDir.invariantSeparatorsPath}' must be a directory."
        }
    }

    /** Associates license or exception IDs with the corresponding license data files. */
    private val licenseDataFileForLicenseOrExceptionId: Map<String, File> by lazy {
        buildMap {
            // Process the files in sorted order to get a deterministic effect also in case multiple files define
            // the same license identifier.
            licenseDataDir.listFiles().filter { it.extension == "LICENSE" }.sortedBy { it.name }.forEach { file ->
                val licenseData = runCatching {
                    parseScanCodeLicenseDataFile(file)
                }.getOrElse { e ->
                    logger.warn {
                        "Could not parse ScanCode license data file '${file.name}': ${e.collectMessages()}."
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
                        put(id, file)
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
