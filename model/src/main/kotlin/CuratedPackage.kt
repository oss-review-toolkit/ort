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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * A [Package] including the [PackageCurationResult]s that were applied to it, in order to be able to trace back how the
 * original metadata of the package was modified by applying [PackageCuration]s.
 */
data class CuratedPackage(
    /**
     * The curated package after applying the [curations].
     */
    @JsonAlias("package")
    val metadata: Package,

    /**
     * The concluded license as an [SpdxExpression]. It can be used to override the [declared][declaredLicenses] /
     * [detected][LicenseFinding.license] licenses of a package.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val concludedLicense: SpdxExpression? = null,

    /**
     * The curations in the order they were applied.
     */
    val curations: List<PackageCurationResult> = emptyList()
) : Comparable<CuratedPackage> {
    /**
     * A comparison function to sort packages by their identifier.
     */
    override fun compareTo(other: CuratedPackage) = metadata.id.compareTo(other.metadata.id)

    /**
     * Return a [Package] representing the same package as this one but which does not have any curations applied.
     */
    fun toUncuratedPackage() =
        curations.reversed().fold(this) { current, curation ->
            curation.base.apply(current)
        }.metadata.copy(
            // The declared license mapping cannot be reversed as it is additive.
            declaredLicensesProcessed = DeclaredLicenseProcessor.process(metadata.declaredLicenses)
        )

    @JsonIgnore
    fun getDeclaredLicenseMapping(): Map<String, SpdxExpression> =
        buildMap {
            curations.forEach { putAll(it.curation.declaredLicenseMapping) }
        }
}
