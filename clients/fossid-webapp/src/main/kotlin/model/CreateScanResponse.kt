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
 * This class is present to handle the high polymorphism of the 'createScan' response.
 */
data class CreateScanResponse(
    // Normal response.
    val scanId: String? = null,

    // Error response when there is a credentials issue (see https://github.com/oss-review-toolkit/ort/issues/8462).
    val code: String? = null,
    val message: String? = null,
    val messageParameters: Map<String, String>? = null
)
