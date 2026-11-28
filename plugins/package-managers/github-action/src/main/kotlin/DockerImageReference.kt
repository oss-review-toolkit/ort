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

/** The tag Docker uses if an image reference has neither a tag nor a digest. */
private const val DEFAULT_TAG = "latest"

/**
 * A reference to a Docker image of the form "[registry/][namespace/]name[:tag][@digest]", as used by actions of the
 * form "docker://image".
 */
internal data class DockerImageReference(
    /** The host of the registry, possibly including a port, or an empty string for the default registry. */
    val registry: String,

    /** The path to the image within the registry without the [name], or an empty string. */
    val namespace: String,

    /** The name of the image. */
    val name: String,

    /** The digest of the image if present, otherwise its tag. */
    val version: String
) {
    companion object {
        fun parse(image: String): DockerImageReference {
            val nameWithTag = image.substringBefore('@')
            val digest = image.substringAfter('@', missingDelimiterValue = "")

            // The tag is separated by the first colon after the last slash, as the registry might contain a port.
            val tagSeparatorIndex = nameWithTag.indexOf(':', startIndex = nameWithTag.lastIndexOf('/') + 1)
            val repository = if (tagSeparatorIndex < 0) nameWithTag else nameWithTag.substring(0, tagSeparatorIndex)
            val tag = if (tagSeparatorIndex < 0) "" else nameWithTag.substring(tagSeparatorIndex + 1)

            // Like Docker, only treat the first component as a registry if it looks like a host.
            val components = repository.split('/')
            val hasRegistry = components.size > 1 &&
                components.first().let { it.contains('.') || it.contains(':') || it == "localhost" }
            val pathComponents = if (hasRegistry) components.drop(1) else components

            return DockerImageReference(
                registry = if (hasRegistry) components.first() else "",
                namespace = pathComponents.dropLast(1).joinToString("/"),
                name = pathComponents.last(),
                version = digest.ifEmpty { tag.ifEmpty { DEFAULT_TAG } }
            )
        }
    }
}
