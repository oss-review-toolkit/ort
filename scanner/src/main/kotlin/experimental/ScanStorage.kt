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

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.scanner.ScannerCriteria

/**
 * A reader that reads [ScanResult]s from a storage.
 */
sealed interface ScanStorageReader

/**
 * A [ScanStorageReader]s that reads [ScanResult]s from a storage that stores results associated to [Package]s.
 */
interface PackageBasedScanStorageReader : ScanStorageReader {
    /**
     * Read all [ScanResult]s for the provided [package][pkg]. This includes all stored scan results matching the
     * [package id][Package.id] and [provenance][KnownProvenance.matches], and the provided [nestedProvenance].
     *
     * A [ScanStorageException] is thrown if:
     * * An error occurs while reading from the storage.
     */
    fun read(pkg: Package, nestedProvenance: NestedProvenance): List<NestedProvenanceScanResult>

    /**
     * Like [read], but also filters by the provided [scannerCriteria].
     */
    fun read(
        pkg: Package,
        nestedProvenance: NestedProvenance,
        scannerCriteria: ScannerCriteria
    ): List<NestedProvenanceScanResult>
}

/**
 * A [ScanStorageReader] that reads [ScanResult]s from a storage that stores results associated to [Provenance]s.
 */
interface ProvenanceBasedScanStorageReader : ScanStorageReader {
    /**
     * Read all [ScanResult]s for the provided [provenance]. If the [provenance] is an [ArtifactProvenance], the URL and
     * the hash value must match. If the [provenance] is a [RepositoryProvenance], the VCS type and URL, and the
     * resolved revision must match. The VCS revision is ignored, because the resolved revision already defines what was
     * scanned.
     *
     * A [ScanStorageException] is thrown if:
     * * An error occurs while reading from the storage.
     * * The [provenance] is a [RepositoryProvenance] with a non-empty VCS path.
     */
    fun read(provenance: KnownProvenance): List<ScanResult>

    /**
     * Like [read], but also filters by the provided [scannerCriteria].
     */
    fun read(provenance: KnownProvenance, scannerCriteria: ScannerCriteria): List<ScanResult> =
        read(provenance).filter { scannerCriteria.matches(it.scanner) }
}

/**
 * A writer that writes [ScanResult]s to a storage.
 */
sealed interface ScanStorageWriter

/**
 * A [ScanStorageWriter] that writes [ScanResult]s to a storage that stores results associated to [Package]s.
 */
interface PackageBasedScanStorageWriter : ScanStorageWriter {
    /**
     * Write the provided [nestedProvenanceScanResult] to the storage, associated to the provided [package][pkg].
     *
     * A [ScanStorageException] is thrown if:
     * * An error occurs while writing to the storage.
     * * The storage already contains a result for the same provenance and scanner
     * * The provenance of the package is [unknown][UnknownProvenance].
     */
    fun write(pkg: Package, nestedProvenanceScanResult: NestedProvenanceScanResult)
}

/**
 * A [ScanStorageWriter] that writer [ScanResult]s to a storage that stores results by their [Provenance].
 */
interface ProvenanceBasedScanStorageWriter : ScanStorageWriter {
    /**
     * Write the provided [scanResult] to the storage. The [scanResult] must have a [KnownProvenance], and if it has a
     * [RepositoryProvenance] the VCS path must be empty, because only scan results for complete repositories are
     * stored.
     *
     * A [ScanStorageException] is thrown if:
     * * An error occurs while writing to the storage.
     * * The storage already contains a result for the same provenance and scanner.
     * * The provenance of the [scanResult] is [unknown][UnknownProvenance].
     * * The provenance of the [scanResult] is a [RepositoryProvenance] with a non-empty VCS path.
     */
    fun write(scanResult: ScanResult)
}

/**
 * A combination of the [ScanStorageReader] and [ScanStorageWriter]. This is a markup interface used when it is not
 * known or relevant which actual implementation a storage class provides.
 */
sealed interface ScanStorage : ScanStorageReader, ScanStorageWriter

/**
 * A combination of the [PackageBasedScanStorageReader] and [PackageBasedScanStorageWriter]. This interface is usually
 * implemented by actual storage implementations, because it is often convenient to implement both interfaces in a
 * single class.
 */
interface PackageBasedScanStorage : ScanStorage, PackageBasedScanStorageReader, PackageBasedScanStorageWriter

/**
 * A combination of the [ProvenanceBasedScanStorageReader] and [ProvenanceBasedScanStorageWriter]. This interface is
 * usually implemented by actual storage implementations, because it is often convenient to implement both interfaces in
 * a single class.
 */
interface ProvenanceBasedScanStorage : ScanStorage, ProvenanceBasedScanStorageReader, ProvenanceBasedScanStorageWriter
