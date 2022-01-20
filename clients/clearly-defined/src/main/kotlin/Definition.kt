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

package org.ossreviewtoolkit.clients.clearlydefined

import com.fasterxml.jackson.annotation.JsonInclude

import java.io.File
import java.net.URI

/**
 * See https://github.com/clearlydefined/service/blob/b339cb7/schemas/definition-1.0.json#L48-L61.
 */
data class Meta(
    val schemaVersion: String,
    val updated: String
)

/**
 * See https://github.com/clearlydefined/service/blob/b339cb7/schemas/definition-1.0.json#L80-L89.
 */
data class FinalScore(
    val effective: Int,
    val tool: Int
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L90-L134.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FileEntry(
    val path: File,
    val license: String? = null,
    val attributions: List<String>? = null,
    val facets: Facets? = null,
    val hashes: Hashes? = null,
    val token: String? = null,
    val natures: Set<Nature>? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L135-L144.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Hashes(
    val md5: String? = null,
    val sha1: String? = null,
    val sha256: String? = null,
    val gitSha: String? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L145-L179.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Described(
    val score: DescribedScore? = null,
    val toolScore: DescribedScore? = null,
    val facets: Facets? = null,
    val sourceLocation: SourceLocation? = null,
    val urls: URLs? = null,
    val projectWebsite: URI? = null,
    val issueTracker: URI? = null,
    val releaseDate: String? = null,
    val hashes: Hashes? = null,
    val files: Int? = null,
    val tools: List<String>? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L180-L190.
 */
data class DescribedScore(
    val total: Int,
    val date: Int,
    val source: Int
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L191-L204.
 */
data class LicensedScore(
    val total: Int,
    val declared: Int,
    val discovered: Int,
    val consistency: Int,
    val spdx: Int,
    val texts: Int
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L211-L235.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L236-L253.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class URLs(
    val registry: URI? = null,
    val version: URI? = null,
    val download: URI? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L254-L263.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Licensed(
    val score: LicensedScore? = null,
    val toolScore: LicensedScore? = null,
    val declared: String? = null,
    val facets: Facets? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L264-L275.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Facets(
    val core: Facet? = null,
    val data: Facet? = null,
    val dev: Facet? = null,
    val doc: Facet? = null,
    val examples: Facet? = null,
    val tests: Facet? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L276-L286.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Facet(
    val files: Int? = null,
    val attribution: Attribution? = null,
    val discovered: Discovered? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L287-L301.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Attribution(
    val parties: List<String>? = null,
    val unknown: Int? = null
)

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L305-L319.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Discovered(
    val expressions: List<String>? = null,
    val unknown: Int? = null
)
