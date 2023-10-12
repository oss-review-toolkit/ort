/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.clearlydefined

import kotlinx.serialization.Serializable

/**
 * See https://github.com/clearlydefined/service/blob/b339cb7/schemas/curations-1.0.json#L64-L83 and
 * https://docs.clearlydefined.io/using-data#a-note-on-definition-coordinates.
 */
@Serializable(CoordinatesSerializer::class)
data class Coordinates(
    /**
     * The type of the component. For example, npm, git, nuget, maven, etc. This talks about the shape of the
     * component.
     */
    val type: ComponentType,

    /**
     * Where the component can be found. Examples include npmjs, mavencentral, github, nuget, etc.
     */
    val provider: Provider,

    /**
     * Many component systems have namespaces: GitHub orgs, NPM namespace, Maven group id, etc. This segment must be
     * supplied. If your component does not have a namespace, use '-' (ASCII hyphen).
     */
    val namespace: String? = null,

    /**
     * The name of the component. Given the mentioned [namespace] segment, this is just the simple name.
     */
    val name: String,

    /**
     * Components typically have some differentiator like a version or commit id. Use that here. If this segment is
     * omitted, the latest revision is used (if that makes sense for the provider).
     */
    val revision: String? = null
) {
    constructor(value: String) : this(value.split('/', limit = 5))

    private constructor(parts: List<String>) : this(
        type = ComponentType.fromString(parts[0]),
        provider = Provider.fromString(parts[1]),
        namespace = parts[2].takeUnless { it == "-" },
        name = parts[3],
        revision = parts[4].takeUnless { it.isEmpty() }
    )

    private val strings: List<String> =
        listOf(type.toString(), provider.toString(), namespace ?: "-", name, revision.orEmpty())

    override fun toString() = strings.joinToString("/")
}

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L211-L235.
 */
@Serializable
data class SourceLocation(
    // The following properties match those of Coordinates, except that the revision is mandatory here.
    val type: ComponentType,
    val provider: Provider,
    val namespace: String? = null,
    val name: String,
    val revision: String,

    val path: String? = null,
    val url: String? = null
)

/**
 * Convert a [SourceLocation] to a [Coordinates] object.
 */
fun SourceLocation.toCoordinates(): Coordinates = Coordinates(type, provider, namespace, name, revision)
