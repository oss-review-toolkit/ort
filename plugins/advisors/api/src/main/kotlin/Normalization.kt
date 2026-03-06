/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.api

import org.metaeffekt.core.security.cvss.CvssVector

import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference

fun AdvisorResult.normalizeVulnerabilityData(): AdvisorResult =
    copy(vulnerabilities = vulnerabilities.normalizeVulnerabilityData())

fun List<Vulnerability>.normalizeVulnerabilityData(): List<Vulnerability> =
    map { vulnerability ->
        val normalizedReferences = vulnerability.references.map { reference ->
            reference
                .run {
                    // Treat "MODERATE" as an alias for "MEDIUM" independently of the scoring system.
                    if (severity == "MODERATE") copy(severity = "MEDIUM") else this
                }
                .run {
                    // Reconstruct the base score from the vector if possible.
                    if (score == null && vector != null) {
                        val score = CvssVector.parseVector(vector)?.baseScore?.toFloat()
                        copy(score = score)
                    } else {
                        this
                    }
                }
                .run {
                    // Reconstruct the severity from the scoring system and score if possible.
                    if (severity == null && scoringSystem != null && score != null) {
                        val severity = VulnerabilityReference.getQualitativeRating(scoringSystem, score)?.name
                        copy(severity = severity)
                    } else {
                        this
                    }
                }
        }

        vulnerability.copy(references = normalizedReferences)
    }
