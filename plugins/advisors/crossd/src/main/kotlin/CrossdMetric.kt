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

import kotlin.math.floor

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive

import org.ossreviewtoolkit.model.Criticality

data class CrossdMetric(

    /**
     * The name of the metric
     */
    val name: String,

    /**
     * The formatted name of the metric
     */
    val displayName: String,

    /**
     * A short description of the metric
     */
    val descriptionShort: String,

    /**
     * A link to the documentation of the metric
     */
    val documentationUrl: String,

    /**
     * Whether a higher value means that the value is better
     */
    val higherIsBetter: Boolean,

    /**
     * The average value as returned by
     * [the CrOSSD API](https://fh-crossd.github.io/components/api/api.html#apimetricsavg) as of *2026-05-23*
     * This is used only as a fallback if the newest values could not be fetched.
     */
    var averageValue: Double,

    /**
     * A function to get the value from the JSON object
     * The default expects a double value in the 'metrics' object with key=name
     * May be overridden for nested structures or different names
     */
    val valueGetter: (JsonObject) -> Double? = { it[name]?.jsonPrimitive?.double }
) {

    /**
     * Calculate the criticality of a value
     * It's based on the same evaluation method that CrOSSD uses:
     * It calculates the difference to the average (in percent) and
     * uses fixed thresholds (changeable in config) for the rating.
     *
     * If value is higher than avg and *higherIsBetter* is set (or vice versa) then
     * it is always considered LOW
     *
     * The values are multiplied by 100 to circumvent floating point errors, especially for the unit tests.
     */
    fun getCriticality(value: Double, thresholds: Map<Criticality, Int>): Criticality {
        val percentWorse = floor((1 - (value / averageValue)) * (if (higherIsBetter) 10000 else -10000)) / 100

        for ((criticality, threshold) in thresholds) {
            if (percentWorse <= threshold) return criticality
        }

        return Criticality.Critical
    }
}
