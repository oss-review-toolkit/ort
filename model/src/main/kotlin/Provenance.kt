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

import com.fasterxml.jackson.annotation.JsonInclude
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
     * The original [VcsInfo] that was used to download the source code. It can be different to [vcsInfo] if any
     * automatic detection took place. For example if the original [VcsInfo] does not contain any revision and the
     * revision was automatically detected by searching for a tag that matches the version of the package there
     * would be no way to match the package to the [Provenance] without downloading the source code and searching
     * for the tag again.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val originalVcsInfo: VcsInfo? = null
) : KnownProvenance() {
    override fun matches(pkg: Package): Boolean {
        // If no VCS information is present either, or it does not have a resolved revision, there is no way of
        // verifying matching provenance.
        if (vcsInfo.resolvedRevision == null) return false

        // If pkg.vcsProcessed equals originalVcsInfo or vcsInfo this provenance was definitely created when
        // downloading this package.
        if (pkg.vcsProcessed == originalVcsInfo || pkg.vcsProcessed == vcsInfo) return true

        return listOf(pkg.vcs, pkg.vcsProcessed).any {
            if (it.resolvedRevision != null) {
                it.resolvedRevision == vcsInfo.resolvedRevision
            } else {
                it.revision == vcsInfo.revision || it.revision == vcsInfo.resolvedRevision
            } && it.type == vcsInfo.type && it.url == vcsInfo.url && it.path == vcsInfo.path
        }
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
                val originalVcsInfo = node["original_vcs_info"]?.let { jsonMapper.treeToValue<VcsInfo>(it)!! }
                RepositoryProvenance(vcsInfo, originalVcsInfo)
            }
            else -> UnknownProvenance
        }
    }
}
