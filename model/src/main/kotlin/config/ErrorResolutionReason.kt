/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model.config

enum class ErrorResolutionReason {
    /**
     * The error is due to the way a package is built.
     */
    BUILD_TOOL_OF,

    /**
     * The error can not be fixed as it requires a fix to be made by 3rd party.
     */
    CANT_FIX_ISSUE,

    /**
     * The error is due to an irrelevant scanner issue, such as time out on a large file that is not distributed.
     */
    SCANNER_ISSUE
}
