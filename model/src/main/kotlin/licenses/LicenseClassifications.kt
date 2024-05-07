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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

interface LicenseClassifications {
    fun getAllCategories(): Set<LicenseCategory>
    fun getCategories(license: SpdxSingleLicenseExpression): Set<LicenseCategory>
    fun getAllLicenses(): Set<SpdxSingleLicenseExpression>
    fun getLicenses(category: String): Set<SpdxSingleLicenseExpression>
}

interface LicenseCategory {
    val name: String
    val description: String
}

class EmptyLicenseClassifications : LicenseClassifications {
    override fun getAllCategories() = emptySet<LicenseCategory>()
    override fun getCategories(license: SpdxSingleLicenseExpression) = emptySet<LicenseCategory>()
    override fun getAllLicenses()= emptySet<SpdxSingleLicenseExpression>()
    override fun getLicenses(category: String)= emptySet<SpdxSingleLicenseExpression>()
}
