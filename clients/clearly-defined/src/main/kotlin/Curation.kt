/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

@file:UseSerializers(FileSerializer::class, URISerializer::class)

package org.ossreviewtoolkit.clients.clearlydefined

import java.io.File
import java.net.URI

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement

@Serializable
data class ContributedCurations(
    val curations: Map<Coordinates, Curation>,
    val contributions: List<JsonElement>
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/curation-1.0.json#L7-L17.
 */
@Serializable
data class Curation(
    val described: CurationDescribed? = null,
    val licensed: CurationLicensed? = null,
    val files: List<CurationFileEntry>? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/0d00f25/schemas/curation-1.0.json#L70-L119.
 */
@Serializable
data class CurationDescribed(
    val facets: CurationFacets? = null,
    val sourceLocation: SourceLocation? = null,
    val projectWebsite: URI? = null,
    val issueTracker: URI? = null,
    val releaseDate: String? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/0d00f25/schemas/curation-1.0.json#L74-L90.
 */
@Serializable
data class CurationFacets(
    val data: List<String>? = null,
    val dev: List<String>? = null,
    val doc: List<String>? = null,
    val examples: List<String>? = null,
    val tests: List<String>? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/0d00f25/schemas/curation-1.0.json#L243-L247.
 */
@Serializable
data class CurationLicensed(
    val declared: String? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/0d00f25/schemas/curation-1.0.json#L201-L229.
 */
@Serializable
data class CurationFileEntry(
    val path: File,
    val license: String? = null,
    val attributions: List<String>? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/b339cb7/schemas/curations-1.0.json#L8-L15.
 */
@Serializable
data class Patch(
    val coordinates: Coordinates,
    val revisions: Map<String, Curation>
)

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
        revision = parts.getOrNull(4)
    )

    override fun toString() = listOfNotNull(type, provider, namespace ?: "-", name, revision).joinToString("/")
}
