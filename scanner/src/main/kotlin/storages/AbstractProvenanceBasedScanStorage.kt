/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.storages

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.scanner.ProvenanceBasedScanStorage
import org.ossreviewtoolkit.scanner.ScannerMatcher

// TODO:
// PR1:
// * Improve docs in ScannerMatcher
// * Make min and max version in scanner matcher optional
// * Remove read(provenance) from interface, it's not used anywhere, instead use match all matcher by default
// * Add abstract provenance storage as below and migrate existing storages
//   * Add hint to docs of interface that abstract class should normally be implemented
// * Rename ScanResultsStorage to AbstractPackageBasedScanStorage and move to this package
//   * Add hint to docs of interface that abstract class should normally be implemented
//
// PR2:
// * Make scan storages plugins
// * Move existing storages to plugin projects

// * Later: Replace min and max version in ScannerMatcher with version range expression

abstract class AbstractProvenanceBasedScanStorage() : ProvenanceBasedScanStorage {
    final override fun read(provenance: KnownProvenance): List<ScanResult> {
        TODO("Not yet implemented")
    }

    final override fun read(provenance: KnownProvenance, scannerMatcher: ScannerMatcher): List<ScanResult> {
        TODO("Not yet implemented")
    }

    abstract fun readInternal(provenance: KnownProvenance): List<ScanResult>

    abstract fun readInternal(provenance: KnownProvenance, scannerMatcher: ScannerMatcher): List<ScanResult>

    final override fun write(scanResult: ScanResult) {
        TODO("Not yet implemented")
    }

    abstract fun writeInternal(scanResult: ScanResult)
}
