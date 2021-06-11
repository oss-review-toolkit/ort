/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified

import com.fasterxml.jackson.annotation.JsonUnwrapped

import org.ossreviewtoolkit.clients.fossid.model.identification.common.LicenseMatchType

data class License(
    val id: Int,

    val type: LicenseMatchType,

    val userId: Int,

    val componentId: Int? = null,

    val identificationId: Int,

    val licenseId: Int,

    val created: String,
    val updated: String
) {
    @JsonUnwrapped(prefix = "file_")
    lateinit var file: LicenseFile
}
