/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.clearlydefined

import kotlinx.serialization.Serializable

/**
 * See https://github.com/clearlydefined/service/blob/c47a989/app.js#L201-L205.
 */
@Serializable
data class ErrorResponse(
    val error: Error
)

@Serializable
data class Error(
    val code: String,
    val message: String,
    val innererror: InnerError
)

@Serializable
data class InnerError(
    val name: String,
    val message: String,
    val stack: String
)
