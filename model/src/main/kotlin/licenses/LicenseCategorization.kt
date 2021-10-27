/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

/**
 * A class for configuring metadata for a specific license referred to by a SPDX license identifier.
 *
 * The metadata consists of assignments to generic categories whose exact meaning is customer specific.
 * The categories a license belong to can be evaluated by other components, such as rules or templates,
 * which can decide - based on this information - how to handle a specific license.
 */
data class LicenseCategorization(
    /**
     * The [SpdxSingleLicenseExpression] of this [LicenseCategorization].
     */
    val id: SpdxSingleLicenseExpression,

    /**
     * The identifiers of the [license categories][LicenseCategory] this license is assigned to.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val categories: Set<String>
)
