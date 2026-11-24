/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.spring

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.ossreviewtoolkit.utils.common.withoutSuffix
import org.ossreviewtoolkit.utils.ort.downloadText
import org.ossreviewtoolkit.utils.ort.okHttpClient

internal typealias SpringSubProjectPaths = Map<String, String>

/** Return a list of paths for the given GitHub [owner], [repo], and [revision], wrapped in a [Result]. */
internal fun getGitHubTree(owner: String, repo: String, revision: String): Result<List<String>> =
    okHttpClient
        .downloadText("https://api.github.com/repos/$owner/$repo/git/trees/$revision?recursive=1")
        .mapCatching { json ->
            Json.parseToJsonElement(json).jsonObject.getValue("tree").jsonArray.map {
                it.jsonObject.getValue("path").jsonPrimitive.content
            }
        }

/**
 * Return the paths to the (Gradle, Maven) subprojects of the Spring project identified by the [orgName] and
 * [projectName], in the [version]. The paths are keyed by the subproject name.
 */
internal fun getSpringSubProjectPaths(orgName: String, projectName: String, version: String): SpringSubProjectPaths {
    val paths = getGitHubTree(orgName, projectName, version)
        .getOrElse { getGitHubTree(orgName, projectName, "v$version").getOrThrow() }

    val projectPaths = when (projectName) {
        "spring-framework", "spring-security" -> {
            paths.mapNotNull { path ->
                val gradleFileBasePath = path.withoutSuffix(".gradle")?.takeIf { "spring-" in it }
                gradleFileBasePath?.let { it.substringAfterLast('/') to it.substringBeforeLast('/') }
            }
        }

        "springdoc-openapi" -> {
            paths.mapNotNull { path ->
                val mavenProjectPath = path.withoutSuffix("/pom.xml")?.takeIf { projectName in it }
                mavenProjectPath?.let { it.substringAfterLast('/') to it }
            }
        }

        else -> {
            paths.mapNotNull { path ->
                val gradleProjectPath = path.withoutSuffix("/build.gradle")?.takeIf { projectName in it }
                gradleProjectPath?.let { it.substringAfterLast('/') to it }
            }
        }
    }

    val projectPathsByName = projectPaths.toMap()

    check(projectPathsByName.size == projectPaths.size) {
        "Ambiguous mapping of project names to project paths."
    }

    return projectPathsByName
}
