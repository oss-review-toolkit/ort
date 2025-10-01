/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.provenance.FakeNestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.FakePackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.FakeProvenanceDownloader
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.ProvenanceDownloader

fun createScanner(
    provenanceDownloader: ProvenanceDownloader = FakeProvenanceDownloader(),
    storageReaders: List<ScanStorageReader> = emptyList(),
    storageWriters: List<ScanStorageWriter> = emptyList(),
    packageProvenanceResolver: PackageProvenanceResolver = FakePackageProvenanceResolver(),
    nestedProvenanceResolver: NestedProvenanceResolver = FakeNestedProvenanceResolver(),
    packageScannerWrappers: List<ScannerWrapper> = emptyList(),
    projectScannerWrappers: List<ScannerWrapper> = emptyList()
) = Scanner(
    ScannerConfiguration(archive = FileArchiverConfiguration(enabled = false)),
    DownloaderConfiguration(),
    provenanceDownloader,
    storageReaders,
    storageWriters,
    packageProvenanceResolver,
    nestedProvenanceResolver,
    mapOf(
        PackageType.PROJECT to projectScannerWrappers,
        PackageType.PACKAGE to packageScannerWrappers
    )
)

fun createScanResult(
    provenance: Provenance,
    scannerDetails: ScannerDetails,
    licenseFindings: Set<LicenseFinding> = setOf(
        LicenseFinding("Apache-2.0", TextLocation("${scannerDetails.name}.txt", 1, 2))
    )
) = ScanResult(
    provenance,
    scannerDetails,
    ScanSummary.EMPTY.copy(licenseFindings = licenseFindings)
)

fun createStoredScanResult(provenance: Provenance, scannerDetails: ScannerDetails) =
    ScanResult(
        provenance,
        scannerDetails,
        ScanSummary.EMPTY.copy(
            licenseFindings = setOf(
                LicenseFinding("Apache-2.0", TextLocation("storage.txt", 1, 2))
            )
        )
    )

fun createStoredNestedScanResult(
    provenance: KnownProvenance,
    scannerDetails: ScannerDetails,
    subRepositories: Map<String, RepositoryProvenance> = emptyMap()
) = NestedProvenanceScanResult(
    NestedProvenance(root = provenance, subRepositories = subRepositories),
    scanResults = mapOf(
        provenance to listOf(createStoredScanResult(provenance, scannerDetails))
    ) + subRepositories.values.associateWith { listOf(createStoredScanResult(it, scannerDetails)) }
)
