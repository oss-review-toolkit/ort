/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.treeToValue

/**
 * Provenance information about the origin of source code.
 */
@JsonDeserialize(using = ProvenanceDeserializer::class)
sealed interface Provenance {
    /**
     * True if this [Provenance] refers to the same source code as [pkg], assuming that it belongs to the package id.
     */
    fun matches(pkg: Package): Boolean
}

object UnknownProvenance : Provenance {
    override fun matches(pkg: Package): Boolean = false
}

sealed interface KnownProvenance : Provenance

/**
 * Provenance information for a source artifact.
 */
data class ArtifactProvenance(
    /**
     * The source artifact that was downloaded.
     */
    val sourceArtifact: RemoteArtifact
) : KnownProvenance {
    override fun matches(pkg: Package): Boolean = sourceArtifact == pkg.sourceArtifact
}

/**
 * Provenance information for a Version Control System location.
 */
data class RepositoryProvenance(
    /**
     * The VCS repository that was downloaded.
     */
    val vcsInfo: VcsInfo,

    /**
     * The VCS revision of the source code that was downloaded, resolved from [vcsInfo] during download. Must not be
     * blank, and must also be fixed revision, e.g. the SHA1 of a Git commit instead of a branch or tag name.
     */
    val resolvedRevision: String
) : KnownProvenance {
    init {
        require(resolvedRevision.isNotBlank()) { "The resolved revision must not be blank." }
    }

    /**
     * Return true if this provenance matches the processed VCS information of the [package][pkg].
     */
    override fun matches(pkg: Package): Boolean = vcsInfo == pkg.vcsProcessed
}

/**
 * A custom deserializer for polymorphic deserialization of [Provenance] without requiring type information.
 */
private class ProvenanceDeserializer : StdDeserializer<Provenance>(Provenance::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Provenance {
        val node = p.codec.readTree<JsonNode>(p)
        return when {
            node.has("source_artifact") -> {
                val sourceArtifact = jsonMapper.treeToValue<RemoteArtifact>(node["source_artifact"])
                ArtifactProvenance(sourceArtifact)
            }
            node.has("vcs_info") -> {
                val vcsInfo = jsonMapper.treeToValue<VcsInfo>(node["vcs_info"])
                // For backward compatibility, if there is no resolved_revision use the revision from vcsInfo.
                val resolvedRevision = node["resolved_revision"]?.textValue() ?: vcsInfo.revision
                RepositoryProvenance(vcsInfo, resolvedRevision)
            }
            else -> UnknownProvenance
        }
    }
}
