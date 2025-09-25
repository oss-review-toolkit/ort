/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.treeToValue

import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME

/**
 * A description of the source code repository that was used as input for ORT.
 */
@JsonDeserialize(using = RepositoryDeserializer::class)
data class Repository(
    /**
     * Provenance wrapper for original VCS information, if present.
     */
    val provenance: RepositoryProvenance,

    /**
     * A map of nested repositories, for example Git submodules or Git-Repo modules. The key is the path to the
     * nested repository relative to the root of the main repository.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val nestedRepositories: Map<String, VcsInfo> = emptyMap(),

    /**
     * The configuration of the repository, parsed from [ORT_REPO_CONFIG_FILENAME].
     */
    val config: RepositoryConfiguration = RepositoryConfiguration()
) {
    companion object {
        /**
         * A constant for a [Repository] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = Repository(
            provenance = RepositoryProvenance(
                vcsInfo = VcsInfo.EMPTY,
                resolvedRevision = HashAlgorithm.SHA1.emptyValue
            ),
            nestedRepositories = emptyMap(),
            config = RepositoryConfiguration()
        )
    }

    /**
     * Return the path of [vcs] relative to [Repository.provenance], or null if [vcs] is neither [Repository.provenance]
     * nor contained in [nestedRepositories].
     */
    fun getRelativePath(vcs: VcsInfo): String? {
        fun VcsInfo.matches(other: VcsInfo) = type == other.type && url == other.url && revision == other.revision

        val normalizedVcs = vcs.normalize()

        if (provenance.vcsInfo.normalize().matches(normalizedVcs)) return ""

        return nestedRepositories.entries.find { (_, nestedVcs) -> nestedVcs.normalize().matches(normalizedVcs) }?.key
    }
}

/**
 * A custom deserializer for [Repository] to support the legacy "vcs" and "vcsProcessed" attributes.
 */
private class RepositoryDeserializer : StdDeserializer<Repository>(Repository::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Repository {
        val node = p.codec.readTree<JsonNode>(p)
        val parsedProvenance = when {
            node.has("vcs") -> {
                // Parse [vcs] and [vcsProcessed] attributes.
                val vcs = jsonMapper.treeToValue<VcsInfo>(node["vcs"])
                val vcsProcess = jsonMapper.treeToValue<VcsInfo>(node["vcs_processed"])

                // Fall back to [vcsProcessed], if [vcs] is empty.
                val vcsInfo = if (vcs != VcsInfo.EMPTY) vcs else vcsProcess

                // Get the [vcs]'s revision.
                // Fall back to [vcsProcessed], if [vcs] has empty revision.
                val resolvedRevision = vcs.revision.ifEmpty {
                    vcsProcess.revision.ifEmpty {
                        HashAlgorithm.SHA1.emptyValue
                    }
                }

                // Build a RepositoryProvenance from the parsed VcsInfo fields.
                RepositoryProvenance(vcsInfo, resolvedRevision)
            }

            else -> {
                // Parse the [provenance], if no legacy fields are present.
                jsonMapper.treeToValue<RepositoryProvenance>(node["provenance"])
            }
        }

        val nestedRepositories = if (node.has("nested_repositories")) {
            jsonMapper.treeToValue<Map<String, VcsInfo>>(node["nested_repositories"])
        } else {
            emptyMap()
        }

        val config = if (node.has("config")) {
            jsonMapper.treeToValue<RepositoryConfiguration>(node["config"])
        } else {
            RepositoryConfiguration()
        }

        return Repository(provenance = parsedProvenance, nestedRepositories, config)
    }
}
