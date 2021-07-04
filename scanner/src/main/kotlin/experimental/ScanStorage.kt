/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.experimental

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.scanner.ScannerCriteria

/**
 * A reader that reads [ScanResult]s from a storage.
 */
sealed interface ScanStorageReader

/**
 * A [ScanStorageReader]s that reads [ScanResult]s from a storage that stores results associated to [Package]s.
 */
interface PackageBasedScanStorageReader : ScanStorageReader {
    fun read(pkg: Package): List<NestedProvenanceScanResult>
    fun read(pkg: Package, scannerCriteria: ScannerCriteria): List<NestedProvenanceScanResult>
}

/**
 * A [ScanStorageReader] that reads [ScanResult]s from a storage that stores results associated to [Provenance]s.
 */
interface ProvenanceBasedScanStorageReader : ScanStorageReader {
    fun read(provenance: KnownProvenance): List<ScanResult>
    fun read(provenance: KnownProvenance, scannerCriteria: ScannerCriteria): List<ScanResult>
}

/**
 * A writer that writes [ScanResult]s to a storage.
 */
sealed interface ScanStorageWriter

/**
 * A [ScanStorageWriter] that writes [ScanResult]s to a storage that stores results associated to [Package]s.
 */
interface PackageBasedScanStorageWriter : ScanStorageWriter {
    fun write(pkg: Package, nestedProvenanceScanResult: NestedProvenanceScanResult)
}

/**
 * A [ScanStorageWriter] that writer [ScanResult]s to a storage that stores results by their [Provenance].
 */
interface ProvenanceBasedScanStorageWriter : ScanStorageWriter {
    fun write(scanResult: ScanResult)
}
