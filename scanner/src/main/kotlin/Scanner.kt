/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
import java.util.SortedSet

abstract class Scanner {
    companion object {
        /**
         * The list of all available scanners. This needs to be initialized lazily to ensure the referred objects,
         * which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    Askalono,
                    BoyterLc,
                    Licensee,
                    ScanCode
            )
        }
    }

    data class Result(val licenses: SortedSet<String>, val errors: SortedSet<String>)

    abstract fun scan(packages: List<Package>, outputDirectory: File, downloadDirectory: File? = null)
            : Map<Package, Result>

    /**
     * Return the Java class name as a simple way to refer to the scanner.
     */
    override fun toString(): String = javaClass.simpleName
}
