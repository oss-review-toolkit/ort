/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.gradleplugin

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import org.apache.maven.model.Dependency
import org.apache.maven.model.Parent
import org.apache.maven.model.Repository
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelBuildingException
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult
import org.apache.maven.model.building.ModelCache
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.model.resolution.ModelResolver

typealias ModelSourceResolver = (groupId: String, artifactId: String, version: String) -> ModelSource2

/**
 * A Maven model builder for POM files.
 */
class FileModelBuilder(resolve: ModelSourceResolver) {
    private val simpleModelCache = SimpleModelCache()
    private val simpleModelResolver = SimpleModelResolver(resolve)

    /**
     * Build the Maven model for the given [pomFile] and return it. Throws [ModelBuildingException] on error.
     */
    fun buildModel(pomFile: File): ModelBuildingResult {
        val request = DefaultModelBuildingRequest().apply {
            isProcessPlugins = false
            isTwoPhaseBuilding = true // Required for dependency management handling.
            modelCache = simpleModelCache
            modelResolver = simpleModelResolver
            modelSource = FileModelSource(pomFile)
            validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
        }

        return DefaultModelBuilderFactory().newInstance().build(request)
    }
}

/**
 * A simple in-memory cache for built Maven models.
 */
private class SimpleModelCache : ModelCache {
    private data class CacheKey(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val tag: String
    )

    private val cache = ConcurrentHashMap<CacheKey, Any>()

    override fun put(groupId: String, artifactId: String, version: String, tag: String, data: Any) {
        val key = CacheKey(groupId, artifactId, version, tag)
        cache[key] = data
    }

    override fun get(groupId: String, artifactId: String, version: String, tag: String): Any? {
        val key = CacheKey(groupId, artifactId, version, tag)
        return cache[key]
    }
}

/**
 * A simple Maven model resolver that does not support repositories.
 */
private class SimpleModelResolver(private val resolve: ModelSourceResolver) : ModelResolver {
    override fun resolveModel(groupId: String, artifactId: String, version: String): ModelSource2 =
        resolve(groupId, artifactId, version)

    override fun resolveModel(parent: Parent): ModelSource2 = resolve(parent.groupId, parent.artifactId, parent.version)

    override fun resolveModel(dependency: Dependency): ModelSource2 =
        resolve(dependency.groupId, dependency.artifactId, dependency.version)

    override fun addRepository(repository: Repository) = Unit
    override fun addRepository(repository: Repository, replace: Boolean) = Unit

    override fun newCopy(): ModelResolver = this
}
