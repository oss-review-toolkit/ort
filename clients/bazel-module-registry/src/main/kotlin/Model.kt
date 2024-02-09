/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.bazelmoduleregistry

import io.ks3.java.typealiases.UriAsString

import kotlinx.serialization.Serializable

/**
 * E.g. https://bcr.bazel.build/modules/glog/metadata.json.
 */
@Serializable
data class ModuleMetadata(
    val homepage: UriAsString?,
    val maintainers: List<Maintainer>? = null,
    val repository: List<String>? = null,
    val versions: List<String>,
    // The key in the map is the version, the value the reason for yanking it.
    val yankedVersions: Map<String, String>
) {
    @Serializable
    data class Maintainer(
        val email: String? = null,
        val name: String? = null
    )
}

/**
 * E.g. https://bcr.bazel.build/modules/glog/0.5.0/source.json.
 */
@Serializable
data class ModuleSourceInfo(
    val integrity: String,
    val patchStrip: Int? = null,
    val patches: Map<String, String>? = null,
    val stripPrefix: String? = null,
    val url: UriAsString
)
