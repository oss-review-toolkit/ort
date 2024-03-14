/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.swiftpm

import java.io.File
import java.io.IOException

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

/**
 * The data model for the output of the command `swift package show-dependencies --format json`.
 */
@Serializable
internal data class SwiftPackage(
    val identity: String,
    val name: String,
    val url: String,
    val version: String,
    val path: String,
    val dependencies: List<SwiftPackage>
)

/**
 * See https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L387-L462.
 */
@Serializable
internal data class PinV2(
    val identity: String,
    val state: PinState?,
    val location: String,
    val kind: Kind
) {
    enum class Kind {
        @SerialName("localSourceControl")
        LOCAL_SOURCE_CONTROL,
        @SerialName("registry")
        REGISTRY,
        @SerialName("remoteSourceControl")
        REMOTE_SOURCE_CONTROL
    }
}

/**
 * See https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L351-L373
 * and https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L440-L461.
 */
@Serializable
internal data class PinState(
    val version: String? = null,
    val revision: String? = null,
    val branch: String? = null
)

/**
 * See https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L285-L384.
 */
@Serializable
private data class PinV1(
    @SerialName("package") val packageName: String,
    val state: PinState?,
    @SerialName("repositoryURL") val repositoryUrl: String
)

internal fun parseLockfile(packageResolvedFile: File): Result<Set<PinV2>> =
    runCatching {
        val root = json.parseToJsonElement(packageResolvedFile.readText()).jsonObject

        when (val version = root.getValue("version").jsonPrimitive.content) {
            "1" -> {
                // See https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L285.
                val projectDir = packageResolvedFile.parentFile
                val pinsJson = root["object"]?.jsonObject?.get("pins")
                pinsJson?.let { json.decodeFromJsonElement<List<PinV1>>(it) }.orEmpty().map { it.toPinV2(projectDir) }
            }

            "2", "3" -> {
                // See https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L387
                // and https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L465.
                val pinsJson = root["pins"]
                pinsJson?.let { json.decodeFromJsonElement<List<PinV2>>(it) }.orEmpty()
            }

            else -> {
                throw IOException(
                    "Could not parse lockfile '${packageResolvedFile.invariantSeparatorsPath}'. Unknown file format " +
                        "version '$version'."
                )
            }
        }.toSet()
    }

internal fun parseSwiftPackage(string: String): SwiftPackage = json.decodeFromString<SwiftPackage>(string)

private fun PinV1.toPinV2(projectDir: File): PinV2 =
    PinV2(
        identity = packageName,
        state = state,
        location = repositoryUrl,
        // Map the 'kind' analog to
        // https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L492-L496:
        kind = if (projectDir.resolve(repositoryUrl).isDirectory) {
            PinV2.Kind.LOCAL_SOURCE_CONTROL
        } else {
            PinV2.Kind.REMOTE_SOURCE_CONTROL
        }
    )
