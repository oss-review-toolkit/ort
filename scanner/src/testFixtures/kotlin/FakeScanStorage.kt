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

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult

/**
 * An implementation of [PackageBasedScanStorageReader] and [PackageBasedScanStorageWriter] that returns scan results
 * with a single license finding for the provided [scannerDetails].
 */
class FakePackageBasedStorageReader(val scannerDetails: ScannerDetails) : PackageBasedScanStorageReader {
    override fun read(
        pkg: Package,
        nestedProvenance: NestedProvenance,
        scannerMatcher: ScannerMatcher?
    ): List<NestedProvenanceScanResult> = listOf(createStoredNestedScanResult(nestedProvenance.root, scannerDetails))
}

class FakeProvenanceBasedStorageReader(val scannerDetails: ScannerDetails) : ProvenanceBasedScanStorageReader {
    override fun read(provenance: KnownProvenance, scannerMatcher: ScannerMatcher?): List<ScanResult> =
        listOf(createStoredScanResult(provenance, scannerDetails))
}

class FakePackageBasedStorageWriter : PackageBasedScanStorageWriter {
    override fun write(pkg: Package, nestedProvenanceScanResult: NestedProvenanceScanResult) = Unit
}

class FakeProvenanceBasedStorageWriter : ProvenanceBasedScanStorageWriter {
    override fun write(scanResult: ScanResult) = true
}
