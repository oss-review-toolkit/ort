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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri

/**
 * A class used in the [OrtConfiguration] to configure [PathExclude]s and [LicenseFindingCuration]s for a specific
 * [Package]'s [Identifier] (and [Provenance]).
 * Note that [PathExclude]s and [LicenseFindingCuration]s for [Project]s are configured by a [RepositoryConfiguration]'s
 * [excludes][RepositoryConfiguration.excludes] and [curations][RepositoryConfiguration.curations] properties instead.
 */
data class PackageConfiguration(
    /**
     * The identifier of the package this configuration applies to.
     */
    val id: Identifier,

    /**
     * The source artifact this configuration applies to.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val sourceArtifactUrl: String? = null,

    /**
     * The vcs and revision this configuration applies to.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val vcs: VcsMatcher? = null,

    /**
     * Path excludes.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val pathExcludes: List<PathExclude> = emptyList(),

    /**
     * License finding curations.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseFindingCurations: List<LicenseFindingCuration> = emptyList()
) {
    init {
        require((sourceArtifactUrl == null) xor (vcs == null)) {
            "A package configuration must either set the 'sourceArtifactUrl' or the 'vcs' property."
        }
    }

    fun matches(otherId: Identifier, provenance: Provenance): Boolean {
        @Suppress("ComplexCondition")
        if (!id.type.equals(otherId.type, ignoreCase = true) ||
            id.namespace != otherId.namespace ||
            id.name != otherId.name ||
            id.version != otherId.version
        ) {
            return false
        }

        return when (provenance) {
            is UnknownProvenance -> false
            is ArtifactProvenance -> sourceArtifactUrl != null && sourceArtifactUrl == provenance.sourceArtifact.url
            is RepositoryProvenance -> vcs != null && vcs.matches(provenance)
        }
    }
}

/**
 * A matcher which matches its properties against a [RepositoryProvenance].
 */
data class VcsMatcher(
    /**
     * The [type] to match for equality against [VcsInfo.type].
     */
    val type: VcsType,

    /**
     * The [url] to match for equality against [VcsInfo.url].
     */
    val url: String,

    /**
     * The [revision] to match for equality against [RepositoryProvenance.resolvedRevision], or null to match any
     * revision.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val revision: String? = null
) {
    init {
        require(url.isNotBlank() && revision?.isBlank() != true)
    }

    fun matches(provenance: RepositoryProvenance): Boolean =
        type == provenance.vcsInfo.type &&
            // URLs need to match only after any credentials have been removed.
            url.replaceCredentialsInUri() == provenance.vcsInfo.url.replaceCredentialsInUri() &&
            (revision == null || revision == provenance.resolvedRevision)
}
