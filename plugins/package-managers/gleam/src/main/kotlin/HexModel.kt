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

package org.ossreviewtoolkit.plugins.packagemanagers.gleam

import kotlinx.serialization.Serializable

/**
 * Response model for the Hex.pm package API.
 * See https://github.com/hexpm/specifications/blob/main/apiary.apib
 */
@Serializable
internal data class HexPackageInfo(
    val name: String,
    val latestStableVersion: String? = null,
    val releases: List<Release> = emptyList(),
    val meta: Meta? = null,
    val owners: List<Owner> = emptyList()
) {
    @Serializable
    data class Release(val version: String)

    @Serializable
    data class Meta(
        val description: String? = null,
        val licenses: List<String> = emptyList(),
        val links: Map<String, String> = emptyMap()
    )

    @Serializable
    data class Owner(
        val username: String? = null
    )
}

/**
 * Response model for the Hex.pm release API.
 */
@Serializable
internal data class HexReleaseInfo(
    val version: String,
    val checksum: String
)

/**
 * Response model for the Hex.pm user API.
 * See https://github.com/hexpm/specifications/blob/main/apiary.apib
 */
@Serializable
internal data class HexUserInfo(
    val username: String,
    val fullName: String? = null,
    val email: String? = null
)
