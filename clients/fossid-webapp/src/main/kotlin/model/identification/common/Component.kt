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

package org.ossreviewtoolkit.clients.fossid.model.identification.common

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import org.ossreviewtoolkit.clients.fossid.model.IntBooleanDeserializer

data class Component(
    val copyright: String?,
    val cpe: String?,
    val id: Long?,

    val includeInReport: Int?,

    @JsonDeserialize(using = IntBooleanDeserializer::class)
    val copyleft: Boolean?,

    val licenseIdentifier: String?,

    val licenseIsFoss: Int?,

    val licenseIsSpdxStandard: Int?,

    val licenseName: String?,
    val name: String?,
    val version: String?,
)
