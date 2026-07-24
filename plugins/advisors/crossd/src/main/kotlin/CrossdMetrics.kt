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

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CrOSSD does not provide a schema of their API responses.
 * And also there is no further information about the metrics in the response besides the raw value.
 * Therefore, we define the metrics that we have an explanation for
 * from the documentation and an average value from the API.
 */
val CROSSD_METRICS: Array<CrossdMetric> = arrayOf(
    CrossdMetric(
        name = "elephant_factor",
        displayName = "Elephant Factor",
        descriptionShort = "This metric expresses the distribution of contributions " +
            "by the community across the companies the contributors belong to.",
        documentationUrl = "https://health.crossd.tech/doc#elephant-factor",
        higherIsBetter = true,
        averageValue = 5.04
    ),
    CrossdMetric(
        name = "maturity_level",
        displayName = "Maturity Level",
        descriptionShort = "Considered as quality metric, this measure includes " +
            "a repositories age, issues and releases.",
        documentationUrl = "https://health.crossd.tech/doc#maturity-level",
        higherIsBetter = true,
        averageValue = 73.82
    ),
    CrossdMetric(
        name = "criticality_score",
        displayName = "Criticality Score",
        descriptionShort = "The project presenting this metric is maintained by member of the Securing Critical " +
            "Projects WG4 and aims to detect critical projects, the open-source community depends on.",
        documentationUrl = "https://health.crossd.tech/doc#criticality-score",
        higherIsBetter = true,
        averageValue = 40.42
    ),
    CrossdMetric(
        name = "support_rate",
        displayName = "Support Rate",
        descriptionShort = "The support rate refers to issues and pulls which " +
            "received a response during the last 90 days.",
        documentationUrl = "https://health.crossd.tech/doc#support-rate",
        higherIsBetter = true,
        averageValue = 34.1
    ),
    CrossdMetric(
        name = "github_community_health_percentage",
        displayName = "GitHub community health percentage",
        descriptionShort = "This percentage score represents the existence of several " +
            "files such as the readme or the contributing file.",
        documentationUrl = "https://health.crossd.tech/doc#github-community-health-percentage-7",
        higherIsBetter = true,
        averageValue = 56.29,
        valueGetter = {
            it["github_community_health_percentage"]?.jsonObject["custom_health_score"]?.jsonPrimitive?.double
        }
    )
)
