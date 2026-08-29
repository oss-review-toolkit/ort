/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File

import org.ossreviewtoolkit.utils.common.realFile

/**
 * This class reads 'package.json' files and caches the reads, so that each file is read at most once.
 */
internal class PackageJsonResolver {
    private val cache = mutableMapOf<File, PackageJson?>()

    fun resolvePackageJson(packageJsonFile: File): PackageJson? {
        val realFile = packageJsonFile.realFile
        return cache.getOrPut(realFile) { parsePackageJson(realFile) }
    }
}
