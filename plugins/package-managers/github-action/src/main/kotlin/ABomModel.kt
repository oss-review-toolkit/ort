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

package org.ossreviewtoolkit.plugins.packagemanagers.githubaction

import kotlin.time.Instant

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

internal val JSON = Json {
    decodeEnumsCaseInsensitive = true
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/**
 * See: https://github.com/JulietSecurity/abom/blob/6dd6c79f0ec7a63165bd92608fd02ba5f31541d6/pkg/model/abom.go
 */
@Serializable
internal data class ABom(
    val abom: ABomMetadata,
    val workflows: List<Workflow>,
    val actions: List<Action>,
    val summary: Summary
) {
    @Serializable
    data class ABomMetadata(
        val version: String,
        val tool: String,
        val generated: Instant
    )

    /**
     * See: https://github.com/JulietSecurity/abom/blob/6dd6c79f0ec7a63165bd92608fd02ba5f31541d6/pkg/model/workflow.go
     */
    @Serializable
    data class Workflow(
        val name: String,
        val path: String,
        val jobs: List<Job>
    )

    @Serializable
    data class Job(
        val id: String,
        val steps: List<Step> = emptyList()
    )

    @Serializable
    data class Step(
        val name: String? = null,
        val action: Action? = null
    )

    /**
     * See: https://github.com/JulietSecurity/abom/blob/6dd6c79f0ec7a63165bd92608fd02ba5f31541d6/pkg/model/action.go
     */
    @Serializable
    data class Action(
        val uses: String,
        val owner: String? = null,
        val repo: String? = null,
        val path: String? = null,
        val ref: String? = null,
        val refType: RefType? = null,
        val type: ActionType,
        val pinned: Boolean,
        val dependencies: List<Action> = emptyList()
    )

    enum class RefType {
        SHA,
        TAG,
        BRANCH
    }

    enum class ActionType {
        STANDARD,
        SUBDIRECTORY,
        LOCAL,
        DOCKER,
        REUSABLE
    }

    @Serializable
    data class Summary(
        val totalWorkflows: Int,
        val totalActions: Int,
        val totalTransitive: Int,
        val pinnedToSha: Int,
        val pinnedToTag: Int,
        val compromised: Int
    )
}
