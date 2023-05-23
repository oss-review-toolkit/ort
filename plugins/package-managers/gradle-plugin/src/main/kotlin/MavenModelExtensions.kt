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

import OrtVcsModel

import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import org.apache.maven.model.building.ModelBuildingResult

fun Model.collectAuthors(): Set<String> =
    // The `SetBuilder` [1] returned by `buildSet {}` cannot be deserialized to a `(Linked)HashSet`, so manually call
    // `toSet()`.
    //
    // [1]: https://github.com/JetBrains/kotlin/blob/00e8dc1/libraries/stdlib/jvm/src/kotlin/collections/builders/SetBuilder.kt
    buildList {
        organization?.let {
            if (!it.name.isNullOrEmpty()) add(it.name)
        }

        val developers = developers.mapNotNull { it.organization.orEmpty().ifEmpty { it.name } }
        addAll(developers)
    }.toSet()

fun Model.collectLicenses(): Set<String> =
    licenses.mapNotNullTo(mutableSetOf()) { license ->
        listOfNotNull(license.name, license.url, license.comments).firstOrNull { it.isNotBlank() }
    }

fun ModelBuildingResult.getVcsModel(): OrtVcsModel? {
    val scm = getOriginalScm() ?: return null

    return OrtVcsModelImpl(
        connection = scm.connection.orEmpty(),
        tag = scm.tag?.takeIf { it != "HEAD" }.orEmpty(),
        browsableUrl = scm.url.orEmpty()
    )
}

fun ModelBuildingResult.getOriginalScm(): Scm? {
    val scm = effectiveModel.scm
    var parent = effectiveModel.parent

    while (parent != null) {
        val parentModel = getRawModel("${parent.groupId}:${parent.artifactId}:${parent.version}")

        parentModel.scm?.let { parentScm ->
            parentScm.connection?.let { parentConnection ->
                if (parentConnection.isNotBlank() && scm.connection.startsWith(parentConnection)) {
                    scm.connection = parentScm.connection
                }
            }

            parentScm.url?.let { parentUrl ->
                if (parentUrl.isNotBlank() && scm.url.startsWith(parentUrl)) {
                    scm.url = parentScm.url
                }
            }
        }

        parent = parentModel.parent
    }

    return scm
}
