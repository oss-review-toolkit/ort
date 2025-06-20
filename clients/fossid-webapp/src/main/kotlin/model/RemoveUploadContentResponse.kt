/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.fossid.model

/**
 * This class is present to handle the high polymorphism of the 'removeUploadedContent' response.
 */
data class RemoveUploadContentResponse @JvmOverloads constructor(
    // Normal response.
    val value: Boolean? = null,

    // Error response when e.g. the file does not exist
    val code: String? = null,
    val message: String? = null,
    // Another inconstancy from FossID: 'messageParameters' is an object in [CreateScanResponse] while it is an array
    // in [RemoveUploadContentResponse].
    @Suppress("ArrayInDataClass")
    val messageParameters: Array<String>? = null
)
