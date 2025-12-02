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

internal fun getGitHubTree(owner: String, repo: String, revision: String): List<String> {
    val json = okHttpClient
        .downloadText("https://api.github.com/repos/$owner/$repo/git/trees/$revision?recursive=1")
        .getOrThrow()

    return Json.parseToJsonElement(json).jsonObject.getValue("tree").jsonArray.map {
        it.jsonObject.getValue("path").jsonPrimitive.content
    }
}

internal fun getSpringProjectPaths(projectName: String, projectVersion: String): Map<String, String> {
    val paths = getGitHubTree("spring-projects", projectName, "v$projectVersion")

    val projectPaths = paths.mapNotNull { path ->
        path.withoutSuffix("/build.gradle")?.takeIf { it.startsWith(projectName) }
    }

    val projectPathsByName = projectPaths.associateBy { it.substringAfterLast('/') }

    check(projectPathsByName.size == projectPaths.size) {
        "Ambiguous mapping of project names to project paths."
    }

    return projectPathsByName
}
