/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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

import java.io.File
import java.util.ServiceLoader

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.ScannerCriteria

/**
 * The base interface for all types of scanners.
 */
sealed interface ScannerWrapper {
    companion object {
        private val LOADER = ServiceLoader.load(ScannerWrapperFactory::class.java)!!

        /**
         * The set of all available [scanner wrapper factories][ScannerWrapperFactory] in the classpath, sorted by name.
         */
        val ALL: Set<ScannerWrapperFactory> by lazy {
            LOADER.iterator().asSequence().toSortedSet(compareBy { it.scannerName })
        }
    }

    /**
     * The name of the scanner.
     */
    val name: String

    /**
     * The details of the scanner.
     */
    val details: ScannerDetails

    /**
     * The [ScannerCriteria] object to be used when looking up existing scan results from a scan storage. By default,
     * the properties of this object are initialized by the scanner implementation. These defaults can be overridden
     * with the [ScannerConfiguration.options] property: Use properties of the form _scannerName.criteria.property_,
     * where _scannerName_ is the name of the scanner and _property_ is the name of a property of the [ScannerCriteria]
     * class. For instance, to specify that a specific minimum version of ScanCode is allowed, set this property:
     * `options.ScanCode.criteria.minScannerVersion=3.0.2`.
     *
     * If this property is null it means that the results of this [ScannerWrapper] cannot be stored in a scan storage.
     */
    val criteria: ScannerCriteria?

    /**
     * Filter the scanner-specific options to remove / obfuscate any secrets, like credentials.
     */
    fun filterSecretOptions(options: Options): Options
}

/**
 * A wrapper interface for scanners that operate on [Package]s and download the package source code themselves.
 */
interface PackageScannerWrapper : ScannerWrapper {
    fun scanPackage(pkg: Package, context: ScanContext): ScanResult
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
