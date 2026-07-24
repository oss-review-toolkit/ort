/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.crossd

import org.ossreviewtoolkit.clients.crossd.CROSSD_BASE_URL
import org.ossreviewtoolkit.model.Criticality
import org.ossreviewtoolkit.plugins.api.OrtPluginOption

/**
 * The configuration for the CrOSSD project health provider.
 */

data class CrossdConfig(
    /** The URL of the CrOSSD server. */
    @OrtPluginOption(defaultValue = CROSSD_BASE_URL)
    val serverUrl: String,

    /** The maximum difference between value and average (in percent) such that the criticality is considered LOW */
    @OrtPluginOption(defaultValue = "15")
    val thresholdCriticalityLow: Int,

    /** The maximum difference between value and average (in percent) such that the criticality is considered MEDIUM */
    @OrtPluginOption(defaultValue = "25")
    val thresholdCriticalityMedium: Int,

    /** The maximum difference between value and average (in percent) such that the criticality is considered HIGH */
    @OrtPluginOption(defaultValue = "40")
    val thresholdCriticalityHigh: Int
) {
    fun getThresholds(): Map<Criticality, Int> =
        linkedMapOf(
            Criticality.Low to thresholdCriticalityLow,
            Criticality.Medium to thresholdCriticalityMedium,
            Criticality.High to thresholdCriticalityHigh
        )
}
