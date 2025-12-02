/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.scanner.provenance

import java.io.IOException

import kotlin.collections.forEach

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo

/**
 * An implementation of [PackageProvenanceResolver] that returns the values from the package without performing any
 * validation.
 */
class FakePackageProvenanceResolver : PackageProvenanceResolver {
    override suspend fun resolveProvenance(
        pkg: Package,
        defaultSourceCodeOrigins: List<SourceCodeOrigin>
    ): KnownProvenance {
        defaultSourceCodeOrigins.forEach { sourceCodeOrigin ->
            when (sourceCodeOrigin) {
                SourceCodeOrigin.ARTIFACT -> {
                    if (pkg.sourceArtifact != RemoteArtifact.EMPTY) {
                        return ArtifactProvenance(pkg.sourceArtifact)
                    }
                }

                SourceCodeOrigin.VCS -> {
                    if (pkg.vcsProcessed != VcsInfo.EMPTY) {
                        return RepositoryProvenance(pkg.vcsProcessed, "resolvedRevision")
                    }
                }
            }
        }

        throw IOException()
    }
}
