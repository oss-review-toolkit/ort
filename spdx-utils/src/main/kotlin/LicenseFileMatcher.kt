/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.spdx

import java.nio.file.FileSystems
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/**
 * A class to determine whether a path resolves to a license file.
 */
class LicenseFileMatcher(
    /**
     * The list of [license files names][licenseFileNames] to consider for matching.
     */
    licenseFileNames: List<String>
) {
    companion object {
        /**
         * A default list of names which are commonly used for license files.
         */
        val DEFAULT_NAMES = listOf(
            "license*",
            "licence*",
            "unlicense",
            "unlicence",
            "copying*",
            "copyright",
            "patents",
            "readme*"
        ).flatMap { listOf(it, it.toUpperCase(), it.capitalize()) }

        /**
         * A matcher which uses the default license file names.
         */
        val DEFAULT_MATCHER = LicenseFileMatcher(DEFAULT_NAMES)
    }

    constructor(vararg licenseFileNames: String) : this(licenseFileNames.toList())

    private val matchers = licenseFileNames.map {
        FileSystems.getDefault().getPathMatcher("glob:$it")
    }

    /**
     * Return true if and only if the given [path] is matched by any of the license file globs passed to the
     * constructor.
     */
    fun matches(path: String): Boolean =
        try {
            matchers.any { it.matches(Paths.get(path)) }
        } catch (e: InvalidPathException) {
            false
        }
}
