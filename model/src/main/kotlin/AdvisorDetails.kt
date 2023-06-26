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

package org.ossreviewtoolkit.model

import java.util.EnumSet

/**
 * Details about the used provider of vulnerability information.
 */
data class AdvisorDetails(
    /**
     * The name of the used advisor.
     */
    val name: String,

    /**
     * The capabilities of the used advisor. This property indicates, which kind of findings are retrieved by the
     * advisor.
     */
    val capabilities: EnumSet<AdvisorCapability>
) {
    init {
        require(capabilities.isNotEmpty()) {
            "An advisor must have at least one capability."
        }
    }
}
