/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.containExactlyInAnyOrder

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.utils.getDetectedLicensesForId
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.spdx.toSpdx

class LicenseViewLicensesTest : AbstractLicenseViewTest() {
    override fun LicenseView.getLicensesWithSources(
        pkg: Package
    ): List<Pair<SpdxSingleLicenseExpression, LicenseSource>> =
        licenses(pkg, ortResult.getDetectedLicensesForId(pkg.id).map { SpdxSingleLicenseExpression.parse(it) })

    override fun containLicensesWithSources(
        vararg licenses: Pair<String, LicenseSource>
    ): Matcher<List<Pair<SpdxExpression, LicenseSource>>?> =
        containExactlyInAnyOrder(licenses.map { Pair(it.first.toSpdx(), it.second) })
}
