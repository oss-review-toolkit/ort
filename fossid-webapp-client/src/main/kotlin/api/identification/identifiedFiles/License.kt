/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.fossid.api.identification.identifiedFiles

import com.fasterxml.jackson.annotation.JsonProperty

import org.ossreviewtoolkit.fossid.api.identification.common.LicenseMatchType

class License {
    @JsonProperty("file_license_match_type")
    var fileLicenseMatchType: LicenseMatchType? = null

    @JsonProperty("id")
    var id: Int? = null

    @JsonProperty("identifier")
    var identifier: String? = null

    @JsonProperty("is_foss")
    var isFoss: Int? = null

    @JsonProperty("is_osi_approved")
    var isOsiApproved: Int? = null

    @JsonProperty("is_spdx_standard")
    var isSpdxStandard: Int? = null

    @JsonProperty("name")
    var name: String? = null
}
