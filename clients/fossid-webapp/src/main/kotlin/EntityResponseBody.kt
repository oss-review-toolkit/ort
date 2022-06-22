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

package org.ossreviewtoolkit.clients.fossid

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class EntityResponseBody<T>(
    val operation: String? = null,
    val status: Int? = null,
    val message: String? = null,
    val error: String? = null,

    val data: T? = null
)

typealias MapResponseBody<T> = EntityResponseBody<Map<String, T>>

typealias PolymorphicResponseBody<T> = EntityResponseBody<PolymorphicList<T>>

class PolymorphicList<T>(data: List<T> = listOf()) : List<T> by data

class PolymorphicInt(val value: Int?)
