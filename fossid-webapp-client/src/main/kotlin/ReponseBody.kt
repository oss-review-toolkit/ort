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

package org.ossreviewtoolkit.fossid

internal open class AbstractPostResponseBody<T> {
    var operation: String? = null
    var status: Int? = null
    var message: String? = null
    var error: String? = null

    open var data: T? = null
}

internal open class EntityPostResponseBody<T> : AbstractPostResponseBody<T>() {
    override var data: T? = null
}

internal class MapResponseBody<T> : AbstractPostResponseBody<Map<String, T>>() {
    override var data: Map<String, T>? = null
}

class PostRequestBody(
        val action: String,
        val group: String,
        apiKey: String,
        user: String
) {
    val data: MutableMap<String, String> = mutableMapOf("username" to user, "key" to apiKey)
}
