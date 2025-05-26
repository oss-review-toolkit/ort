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

import java.io.File

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.plugins.api.Plugin
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance

/**
 * The base interface for all types of wrappers for scanners.
 */
sealed interface ScannerWrapper : Plugin {
    /**
     * The version of the scanner.
     */
    val version: String

    /**
     * The configuration of the scanner.
     */
    val configuration: String

    /**
     * A convenience property to access all scanner details at once.
     */
    val details: ScannerDetails
        get() = ScannerDetails(descriptor.id, version, configuration)

    /**
     * The [ScannerMatcher] object to be used when looking up existing scan results from a scan storage. By default,
     * the properties of this object are initialized by the scanner implementation. These defaults can be overridden
     * with the scanner-specific [options][PluginConfig.options] in [ScannerConfiguration.config]: Use properties
     * of the form `scannerName.options.property`, where `scannerName` is the name of the scanner and `property` is the
     * name of a property of the [ScannerMatcher] class. For instance, to specify that a specific minimum version of
     * ScanCode is allowed, set this property: `config.ScanCode.options.minVersion=3.0.2`.
     *
     * If this property is null, it means that the results of this [ScannerWrapper] cannot be read from a scan storage.
     */
    val matcher: ScannerMatcher?

    /**
     * If `true`, scan results for this scanner shall be read from the configured scan storages. Enabling this option
     * requires that the [matcher] is not `null`.
     */
    val readFromStorage: Boolean

    /**
     * If `true`, scan results for this scanner shall be written to the configured scan storages.
     */
    val writeToStorage: Boolean
}

/**
 * A wrapper interface for scanners that operate on [Package]s and download the package source code themselves.
 */
interface PackageScannerWrapper : ScannerWrapper {
    fun scanPackage(nestedProvenance: NestedProvenance?, context: ScanContext): ScanResult
}

/**
 * A wrapper interface for scanners that operate on [Provenance]s and download the source code themselves.
 */
interface ProvenanceScannerWrapper : ScannerWrapper {
    fun scanProvenance(provenance: KnownProvenance, context: ScanContext): ScanResult
}

/**
 * A wrapper interface for scanners that scan the source code in a path on the local filesystem.
 */
interface PathScannerWrapper : ScannerWrapper {
    fun scanPath(path: File, context: ScanContext): ScanSummary
}
