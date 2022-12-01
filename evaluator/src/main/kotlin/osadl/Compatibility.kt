/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.evaluator.osadl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.utils.common.enumSetOf

@Serializable
enum class Compatibility {
    /**
     * Compatibility is inherent as inbound and outbound licenses are the same.
     */
    @SerialName("Same")
    INHERENT,

    /**
     * Inbound and outbound license have been checked to be compatible.
     */
    @SerialName("Yes")
    YES,

    /**
     * Inbound and outbound license have been checked to be incompatible.
     */
    @SerialName("No")
    NO,

    /**
     * Inbound and outbound license may be compatible or not depending on the context (e.g. the way of integration).
     */
    @SerialName("Check dependency")
    CONTEXTUAL,

    /**
     * Inbound and outbound license have not been checked for compatibility.
     */
    @SerialName("Unknown")
    UNKNOWN;

    companion object {
        val COMPATIBLE_VALUES = enumSetOf(INHERENT, YES)
    }
}
