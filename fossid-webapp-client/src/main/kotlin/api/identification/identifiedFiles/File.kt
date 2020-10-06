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

class File {
    var id: String? = null

    @JsonProperty("license_identifier")
    var licenseIdentifier: String? = null

    @JsonProperty("license_include_in_report")
    var licenseIncludeInReport: Int? = null // 0 or 1

    @JsonProperty("license_is_copyleft")
    var licenseIsCopyleft: Int? = null // 0 or 1

    @JsonProperty("license_is_foss")
    var licenseIsFoss: Int? = null // 0 or 1

    @JsonProperty("license_is_spdx_standard")
    var licenseIsSpdxStandard: Int? = null // 0 or 1

    @JsonProperty("license_match_type")
    var licenseMatchType: String? = null

    @JsonProperty("license_name")
    var licenseName: String? = null

    @JsonProperty("licenses")
    var licenses: MutableMap<Int, License >? = null

    @JsonProperty("md5")
    var md5: String? = null

    @JsonProperty("path")
    var path: String? = null

    @JsonProperty("sha1")
    var sha1: String? = null

    @JsonProperty("sha256")
    var sha256: String? = null
}
