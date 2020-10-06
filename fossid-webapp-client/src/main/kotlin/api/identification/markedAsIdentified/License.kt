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

package org.ossreviewtoolkit.fossid.api.identification.markedAsIdentified

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped

import org.ossreviewtoolkit.fossid.api.identification.common.LicenseMatchType

class License {
    var type: LicenseMatchType? = null

    @JsonProperty("user_id")
    var userId: Int? = null

    @JsonProperty("component_id")
    var componentId: Int? = null

    @JsonProperty("identification_id")
    var identificationId: Int? = null

    @JsonProperty("license_id")
    var licenceId: Int? = null

    var created: String? = null
    var updated: String? = null

    @JsonUnwrapped(prefix = "file_")
    var file: LicenseFile? = null
}
