/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.model.ScanResult

import java.io.File
import java.util.ServiceLoader

abstract class Scanner {
    companion object {
        private val LOADER by lazy { ServiceLoader.load(Scanner::class.java)!! }

        /**
         * The list of all available scanners in the classpath.
         */
        val ALL = LOADER.iterator().asSequence().toList()
    }

    /**
     * Return the Java class name as a simple way to refer to the scanner.
     */
    override fun toString(): String = javaClass.simpleName

    /**
     * Scan the list of [packages] using this [Scanner] and store the scan results in [outputDirectory]. If
     * [downloadDirectory] is specified, it is used instead of [outputDirectory] to download the source code to.
     * [ScanResult]s are returned associated by the [Package]. The map may contain multiple results for the same
     * [Package] if the cache contains more than one result for the specification of this scanner.
     */
    abstract fun scan(packages: List<Package>, outputDirectory: File, downloadDirectory: File? = null)
            : Map<Package, List<ScanResult>>
}
