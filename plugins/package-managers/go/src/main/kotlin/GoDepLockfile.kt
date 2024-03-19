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

package org.ossreviewtoolkit.plugins.packagemanagers.go

import java.io.File

import kotlinx.serialization.Serializable

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.decodeFromNativeReader

private val toml = Toml { ignoreUnknownKeys = true }

/**
 * See https://golang.github.io/dep/docs/Gopkg.lock.html.
 */
@Serializable
internal data class GoDepLockfile(
    val projects: List<Project> = emptyList()
) {
    @Serializable
    data class Project(
        val name: String,
        val packages: List<String>,
        val revision: String,
        val version: String? = null
    )
}

internal fun parseGoDepLockfile(goPkgLock: File): GoDepLockfile =
    goPkgLock.reader().use { toml.decodeFromNativeReader<GoDepLockfile>(it) }
