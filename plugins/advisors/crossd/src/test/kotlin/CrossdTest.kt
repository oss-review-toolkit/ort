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
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Criticality

class CrossdTest : StringSpec({
    val testConfig = CrossdConfig(
        serverUrl = "https://health.crossd.tech",
        thresholdCriticalityLow = 15,
        thresholdCriticalityMedium = 25,
        thresholdCriticalityHigh = 30
    )
    val testThresholds = testConfig.getThresholds()

    "higherIsBetterMetric should return the correct criticality" {
        val metric = CrossdMetric(
            name = "test",
            displayName = "Test Metric",
            descriptionShort = "This metric is nice.",
            documentationUrl = "https://example.org/documentation#test",
            higherIsBetter = true,
            averageValue = 10.0
        )
        metric.getCriticality(-100.0, testThresholds) shouldBe Criticality.Critical
        metric.getCriticality(0.0, testThresholds) shouldBe Criticality.Critical
        metric.getCriticality(7.0, testThresholds) shouldBe Criticality.High
        metric.getCriticality(7.5, testThresholds) shouldBe Criticality.Medium
        metric.getCriticality(8.5, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(9.9, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(10.0, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(100.0, testThresholds) shouldBe Criticality.Low
    }

    "lowerIsBetterMetric should return the correct criticality" {
        val metric = CrossdMetric(
            name = "test",
            displayName = "Test Metric",
            descriptionShort = "This metric is nice.",
            documentationUrl = "https://example.org/documentation#test",
            higherIsBetter = false,
            averageValue = 10.0
        )
        metric.getCriticality(-100.0, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(0.0, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(7.0, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(7.5, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(8.5, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(9.9, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(10.0, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(11.5, testThresholds) shouldBe Criticality.Low
        metric.getCriticality(12.0, testThresholds) shouldBe Criticality.Medium
        metric.getCriticality(12.5, testThresholds) shouldBe Criticality.Medium
        metric.getCriticality(13.0, testThresholds) shouldBe Criticality.High
        metric.getCriticality(100.0, testThresholds) shouldBe Criticality.Critical
    }
})
