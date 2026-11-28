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

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration

import java.io.File

import kotlinx.serialization.Serializable

private val YAML = Yaml(configuration = YamlConfiguration(strictMode = false))

/**
 * The metadata file of a GitHub Action, see
 * https://docs.github.com/en/actions/reference/workflows-and-actions/metadata-syntax.
 */
@Serializable
internal data class ActionFile(
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val runs: Runs? = null
) {
    @Serializable
    data class Runs(
        val using: String = "",
        val steps: List<Step> = emptyList()
    )

    @Serializable
    data class Step(
        val uses: String? = null
    )

    /** Whether this is a composite action, which is the only kind of action that can use other actions. */
    val isComposite: Boolean
        get() = runs?.using == "composite"

    /** The references to other actions used by the steps of a composite action. */
    val usedActions: List<String>
        get() = if (isComposite) runs?.steps.orEmpty().mapNotNull { it.uses?.trim()?.ifEmpty { null } } else emptyList()
}

internal fun parseActionFile(file: File): ActionFile = YAML.decodeFromString(ActionFile.serializer(), file.readText())

/**
 * Return whether the given action reference [uses] refers to a local action. In a composite action, local actions are
 * resolved relative to the workspace of the calling workflow, not relative to the repository of the composite action.
 */
internal fun isLocalActionReference(uses: String) = uses.startsWith("./") || uses.startsWith("../")
