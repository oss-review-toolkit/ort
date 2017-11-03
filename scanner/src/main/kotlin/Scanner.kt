/*
 * Copyright (c) 2017 HERE Europe B.V.
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

package com.here.ort.scanner

import com.here.ort.model.Package
import com.here.ort.scanner.scanners.*

import java.io.File

abstract class Scanner {

    companion object {
        /**
         * The list of all available scanners. This needs to be initialized lazily to ensure the referred objects,
         * which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    ScanCode
            )
        }
    }

    /**
     * Scan the provided package for license information. If a scan result is found in the cache, it is used without
     * running the actual scan. If no cached scan result is found, the package's source code is downloaded automatically
     * and scanned afterwards.
     *
     * @param pkg The package to scan.
     * @param outputDirectory The directory to store scan results in.
     *
     * @return The set of found licenses.
     *
     * @throws ScanException In case the package could not be scanned.
     */
    abstract fun scan(pkg: Package, outputDirectory: File): Set<String>

    /**
     * Scan the provided path for license information. Note that no caching will be used in this mode.
     *
     * @param path The directory or file to scan.
     * @param outputDirectory The directory to store scan results in.
     *
     * @return The set of found licenses.
     *
     * @throws ScanException In case the package could not be scanned.
     */
    abstract fun scan(path: File, outputDirectory: File): Set<String>

}
