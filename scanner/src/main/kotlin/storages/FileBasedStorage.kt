/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package com.here.ort.scanner.storages

import ch.frankel.slf4k.*

import com.here.ort.model.Package
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerDetails
import com.here.ort.scanner.ScanResultsStorage
import com.here.ort.utils.log

const val SCAN_RESULTS_FILE_NAME = "scan-results.yml"

abstract class FileBasedStorage : ScanResultsStorage() {
    override fun readFromStorage(pkg: Package, scannerDetails: ScannerDetails): ScanResultContainer {
        val scanResults = read(pkg.id).results.toMutableList()

        if (scanResults.isEmpty()) return ScanResultContainer(pkg.id, scanResults)

        // Only keep scan results whose provenance information matches the package information.
        scanResults.retainAll { it.provenance.matches(pkg) }
        if (scanResults.isEmpty()) {
            log.info {
                "No stored scan results found for $pkg. The following entries with non-matching provenance have " +
                        "been ignored: ${scanResults.map { it.provenance }}"
            }
            return ScanResultContainer(pkg.id, scanResults)
        }

        // Only keep scan results from compatible scanners.
        scanResults.retainAll { scannerDetails.isCompatible(it.scanner) }
        if (scanResults.isEmpty()) {
            log.info {
                "No stored scan results found for $scannerDetails. The following entries with incompatible scanners " +
                        "have been ignored: ${scanResults.map { it.scanner }}"
            }
            return ScanResultContainer(pkg.id, scanResults)
        }

        log.info {
            "Found ${scanResults.size} stored scan result(s) for ${pkg.id.toCoordinates()} that are compatible with " +
                    "$scannerDetails."
        }

        val patchedScanResults = patchScanCodeLicenseRefs(scanResults)

        return ScanResultContainer(pkg.id, patchedScanResults)
    }

    // TODO: Remove this code again once we migrated our scan result storage to contain the new "namespaced" license
    // names for ScanCode.
    internal fun patchScanCodeLicenseRefs(scanResults: List<ScanResult>) =
        scanResults.map { result ->
            if (result.scanner.name == "ScanCode") {
                val findings = result.summary.licenseFindings.map { finding ->
                    if (finding.license.startsWith("LicenseRef-") &&
                        !finding.license.startsWith("LicenseRef-scancode-")
                    ) {
                        val suffix = finding.license.removePrefix("LicenseRef-")
                        val license = "LicenseRef-scancode-$suffix"
                        log.info { "Patched license name '${finding.license}' to '$license'." }
                        finding.copy(license = license)
                    } else {
                        finding
                    }
                }

                result.copy(summary = result.summary.copy(licenseFindings = findings.toSortedSet()))
            } else {
                result
            }
        }
}
