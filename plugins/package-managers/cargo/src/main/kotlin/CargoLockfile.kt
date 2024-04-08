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

package org.ossreviewtoolkit.plugins.packagemanagers.cargo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import net.peanuuutz.tomlkt.Toml

internal val toml = Toml { ignoreUnknownKeys = true }

/**
 * See https://docs.rs/cargo-lock/latest/cargo_lock/struct.Lockfile.html.
 */
@Serializable
internal data class CargoLockfile(
    val version: Int? = null,

    @SerialName("package")
    val packages: List<Package>,

    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * See https://docs.rs/cargo-lock/latest/cargo_lock/package/struct.Package.html.
     */
    @Serializable
    data class Package(
        val name: String,
        val version: String,
        val source: String? = null,
        val checksum: String? = null,
        val dependencies: List<String> = emptyList(),
        val replace: String? = null
    )
}
