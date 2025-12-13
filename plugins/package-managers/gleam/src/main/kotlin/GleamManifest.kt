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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the manifest.toml lockfile for Gleam projects.
 * This file contains the resolved dependency versions and their metadata.
 */
@Serializable
internal data class GleamManifest(
    /** List of all resolved packages including transitive dependencies. */
    val packages: List<Package>,

    /** Direct requirements from the project's gleam.toml. */
    val requirements: Map<String, Requirement>
) {
    /**
     * Represents a resolved package in the manifest.toml lockfile.
     */
    @Serializable
    data class Package(
        /** The package name. */
        val name: String,

        /** The resolved version. */
        val version: String,

        /** The build tools used by this package (e.g., "gleam", "rebar3", "mix"). */
        @SerialName("build_tools")
        val buildTools: List<String> = emptyList(),

        /** The package's direct dependencies (package names). */
        val requirements: List<String> = emptyList(),

        /** The OTP application name for Erlang integration. */
        @SerialName("otp_app")
        val otpApp: String? = null,

        /** The source of the package. */
        val source: SourceType = SourceType.HEX,

        /** The SHA256 checksum of the package tarball (for hex packages). */
        @SerialName("outer_checksum")
        val outerChecksum: String? = null,

        /** The git repository URL (for git packages). */
        val repo: String? = null,

        /** The resolved git commit hash (for git packages). */
        val commit: String? = null,

        /** The local path (for path/local packages). */
        @SerialName("path")
        val localPath: String? = null
    ) {
        @Serializable
        enum class SourceType {
            @SerialName("hex") HEX,
            @SerialName("git") GIT,
            @SerialName("local") LOCAL
        }
    }

    /**
     * Represents a direct requirement from gleam.toml as recorded in the manifest.
     */
    @Serializable
    data class Requirement(
        /** The source of the requirement: "hex", "git", or "path". */
        val source: SourceType = SourceType.HEX,

        /** The version constraint (for hex packages). */
        val version: String? = null
    ) {
        @Serializable
        enum class SourceType {
            @SerialName("hex") HEX,
            @SerialName("git") GIT,
            @SerialName("path") PATH
        }
    }
}
