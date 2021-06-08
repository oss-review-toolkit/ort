/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
sealed class Provenance {
    /**
     * True if this [Provenance] refers to the same source code as [pkg], assuming that it belongs to the package id.
     */
    abstract fun matches(pkg: Package): Boolean
}

object UnknownProvenance : Provenance() {
    override fun matches(pkg: Package): Boolean = false
}

sealed class KnownProvenance : Provenance()

/**
 * Provenance information for a source artifact.
 */
data class ArtifactProvenance(
    /**
     * The source artifact that was downloaded.
     */
    val sourceArtifact: RemoteArtifact
) : KnownProvenance() {
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
     * The VCS revision of the source code that was downloaded. Must equals the [revision][VcsInfo.revision] of
     * [vcsInfo] if [VcsInfo.isResolvedRevision] is true, otherwise it can be different if it was resolved during the
     * download.
     */
    val resolvedRevision: String
) : KnownProvenance() {
    init {
        if (vcsInfo.isResolvedRevision) {
            require(vcsInfo.revision == resolvedRevision) {
                "The revision '${vcsInfo.revision}' of vcsInfo is resolved but does not equals the resolvedRevision " +
                        "'$resolvedRevision' of the provenance."
            }
        }
    }

    override fun matches(pkg: Package): Boolean {
        // If there is no resolved revision, there is no way of verifying matching provenance.
        if (resolvedRevision.isBlank()) return false

        return vcsInfo == pkg.vcsProcessed
    }
}

/**
 * A custom deserializer for polymorphic deserialization of [Provenance] without requiring type information.
 */
private class ProvenanceDeserializer : StdDeserializer<Provenance>(Provenance::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Provenance {
        val node = p.codec.readTree<JsonNode>(p)
        return when {
            node.has("source_artifact") -> {
                val sourceArtifact = jsonMapper.treeToValue<RemoteArtifact>(node["source_artifact"])!!
                ArtifactProvenance(sourceArtifact)
            }
            node.has("vcs_info") -> {
                val vcsInfo = jsonMapper.treeToValue<VcsInfo>(node["vcs_info"])!!
                // For backward compatibility, if there is no resolved_revision use the revision from vcsInfo.
                val resolvedRevision = node["resolved_revision"]?.textValue() ?: vcsInfo.revision
                RepositoryProvenance(vcsInfo, resolvedRevision)
            }
            else -> UnknownProvenance
        }
    }
}
