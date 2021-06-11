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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.StdConverter

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class HttpFileStorageConfiguration(
    /**
     * The URL of the HTTP server, e.g. "https://example.com/storage".
     */
    val url: String,

    /**
     * The query string that is appended to the combination of the URL and some additional path. Some storages process
     * authentication via parameters that are within the final URL, so certain credentials can be stored in this
     * query, e.g, "?user=standard&pwd=123". Thus, the final URL could be
     * "https://example.com/storage/path?user=standard&pwd=123".
     */
    @JsonSerialize(converter = MaskStringConverter::class)
    val query: String = "",

    /**
     * Custom headers that are added to all HTTP requests. As headers are likely to contain sensitive information like
     * credentials, values are masked when this class is serialized with Jackson.
     */
    @JsonSerialize(contentConverter = MaskStringConverter::class)
    val headers: Map<String, String>
)

class MaskStringConverter : StdConverter<String, String>() {
    override fun convert(value: String) = if (value.isNotEmpty()) "***" else ""
}
