/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.licensefactproviders.dir

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseTextCuration
import org.ossreviewtoolkit.model.LicenseTextCurations
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseText

open class SimpleLicenseTextCurationProvider(
    override val descriptor: PluginDescriptor,
    licenseTextCurations: List<LicenseTextCurations>
) : LicenseFactProvider() {
    private val licenseTextCurationsForIdWithoutVersion = licenseTextCurations.distinct().groupBy {
        it.id.withoutVersion
    }

    override fun hasLicenseText(licenseOrExceptionId: String) = false

    override fun getLicenseText(licenseOrExceptionId: String) = null

    override fun hasLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Boolean =
        getCurationsForId(id).any { curation ->
            curation.licenseId.toString() == singleLicenseExpression
        }

    override fun getLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Set<LicenseText> =
        getCurationsForId(id).mapNotNullTo(mutableSetOf()) { curation ->
            curation.licenseText.takeIf {
                curation.licenseId.toString() == singleLicenseExpression
            }?.let { LicenseText(it) }
        }

    private fun getCurationsForId(id: Identifier): Sequence<LicenseTextCuration> =
        licenseTextCurationsForIdWithoutVersion[id.withoutVersion].orEmpty().asIterable().asSequence().filter {
            it.isApplicable(id)
        }.flatMap { it.curations }
}

private val Identifier.withoutVersion: Identifier
    get() = copy(version = "")
