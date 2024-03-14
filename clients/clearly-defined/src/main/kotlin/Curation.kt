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

package org.ossreviewtoolkit.clients.clearlydefined

import io.ks3.java.typealiases.FileAsString
import io.ks3.java.typealiases.UriAsString

import kotlinx.serialization.Serializable
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
    val projectWebsite: UriAsString? = null,
    val issueTracker: UriAsString? = null,
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
    val path: FileAsString,
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
