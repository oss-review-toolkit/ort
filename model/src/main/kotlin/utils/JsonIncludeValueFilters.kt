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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.Resolutions

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
internal class CurationsFilter {
    override fun equals(other: Any?): Boolean =
        other is Curations && other.licenseFindings.isEmpty() && other.packages.isEmpty()
}

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
internal class ExcludesFilter {
    override fun equals(other: Any?): Boolean = other is Excludes && other.paths.isEmpty() && other.scopes.isEmpty()
}

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
internal class IncludesFilter {
    override fun equals(other: Any?): Boolean = other is Includes && other.paths.isEmpty()
}

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
internal class LicenseChoicesFilter {
    override fun equals(other: Any?): Boolean = other is LicenseChoices && other.isEmpty()
}

/**
 * A custom value filter for [PackageLinkage] to work around
 * https://github.com/FasterXML/jackson-module-kotlin/issues/193.
 */
@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist")
internal class PackageLinkageValueFilter {
    override fun equals(other: Any?) = other == PackageLinkage.DYNAMIC
}

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
internal class ResolutionsFilter {
    override fun equals(other: Any?): Boolean =
        other is Resolutions &&
            other.issues.isEmpty() &&
            other.ruleViolations.isEmpty() &&
            other.vulnerabilities.isEmpty()
}
