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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.URI

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import org.ossreviewtoolkit.model.Criticality
import org.ossreviewtoolkit.model.HealthMetric

class CrossdTest : StringSpec({
    val testThresholds = mapOf(
        Criticality.LOW to 15,
        Criticality.MEDIUM to 25,
        Criticality.HIGH to 40
    )

    "higherIsBetterMetric should return the correct criticality" {
        val metric = CrossdMetric(
            name = "test",
            displayName = "Test Metric",
            descriptionShort = "This metric is nice.",
            documentationUrl = URI("https://example.org/documentation#test"),
            higherIsBetter = true,
            averageValue = 10.0
        )

        metric.getCriticality(-100.0, testThresholds) shouldBe Criticality.CRITICAL
        metric.getCriticality(0.0, testThresholds) shouldBe Criticality.CRITICAL
        metric.getCriticality(7.0, testThresholds) shouldBe Criticality.HIGH
        metric.getCriticality(7.5, testThresholds) shouldBe Criticality.MEDIUM
        metric.getCriticality(8.5, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(9.9, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(10.0, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(100.0, testThresholds) shouldBe Criticality.LOW
    }

    "lowerIsBetterMetric should return the correct criticality" {
        val metric = CrossdMetric(
            name = "test",
            displayName = "Test Metric",
            descriptionShort = "This metric is nice.",
            documentationUrl = URI("https://example.org/documentation#test"),
            higherIsBetter = false,
            averageValue = 10.0
        )

        metric.getCriticality(-100.0, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(0.0, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(7.0, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(7.5, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(8.5, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(9.9, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(10.0, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(11.5, testThresholds) shouldBe Criticality.LOW
        metric.getCriticality(12.0, testThresholds) shouldBe Criticality.MEDIUM
        metric.getCriticality(12.5, testThresholds) shouldBe Criticality.MEDIUM
        metric.getCriticality(13.0, testThresholds) shouldBe Criticality.HIGH
        metric.getCriticality(100.0, testThresholds) shouldBe Criticality.CRITICAL
    }

    "toHealthMetric should describe the scale, direction and reference value" {
        val metric = CrossdMetric(
            name = "test",
            displayName = "Test Metric",
            descriptionShort = "This metric is nice.",
            documentationUrl = URI("https://example.org/documentation#test"),
            higherIsBetter = true,
            minValue = 0.0,
            maxValue = 100.0,
            averageValue = 10.0
        )

        metric.toHealthMetric(buildJsonObject { put("test", 7.0) }, testThresholds) shouldBe HealthMetric(
            name = "Test Metric",
            documentation = "This metric is nice.",
            documentationLink = URI("https://example.org/documentation#test"),
            minValue = 0.0,
            maxValue = 100.0,
            higherIsBetter = true,
            value = 7.0,
            referenceValue = 10.0,
            criticality = Criticality.HIGH
        )
    }

    "toHealthMetric should return null if the metrics do not contain a value" {
        val metric = CrossdMetric(
            name = "test",
            displayName = "Test Metric",
            descriptionShort = "This metric is nice.",
            documentationUrl = URI("https://example.org/documentation#test"),
            higherIsBetter = true,
            averageValue = 10.0
        )

        metric.toHealthMetric(JsonObject(emptyMap()), testThresholds) should beNull()
    }
})
