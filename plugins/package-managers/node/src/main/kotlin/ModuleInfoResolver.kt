/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.ossreviewtoolkit.utils.ort.runBlocking

internal class ModuleInfoResolver(
    private val fetchModuleInfos: (workingDir: File, moduleIds: Set<String>) -> Set<PackageJson>
) {
    companion object {
        fun create(fetchModuleInfo: (workingDir: File, moduleId: String) -> PackageJson?): ModuleInfoResolver =
            ModuleInfoResolver { workingDir, moduleIds ->
                runBlocking(Dispatchers.IO.limitedParallelism(20)) {
                    moduleIds.map {
                        async { fetchModuleInfo(workingDir, it) }
                    }.awaitAll()
                }.filterNotNullTo(mutableSetOf())
            }
    }

    lateinit var workingDir: File
    private val cache = mutableMapOf<String, PackageJson>()

    fun getModuleInfo(moduleId: String): PackageJson? = getModuleInfos(setOf(moduleId)).firstOrNull()

    fun getModuleInfos(moduleIds: Set<String>): Set<PackageJson> {
        val cachedResults = moduleIds.mapNotNull { moduleId ->
            cache[moduleId]?.let { moduleId to it }
        }.toMap()

        val fetchedResults = fetchModuleInfos(workingDir, moduleIds - cachedResults.keys)
        fetchedResults.associateByTo(cache) { it.moduleId }

        return buildSet {
            addAll(cachedResults.values)
            addAll(fetchedResults)
        }
    }
}
