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

// The following code uses OSADL's publicly available compatibility matrix for Open Source licenses, see
// https://www.osadl.org/Access-to-raw-data.oss-compliance-raw-data-access.0.html.
//
// Use this rule like:
//
// $ ort evaluate -i scanner/src/funTest/assets/dummy-expected-output-for-analyzer-result.yml --rules-resource /rules/osadl.rules.kts

import org.ossreviewtoolkit.evaluator.osadl.Compatibility
import org.ossreviewtoolkit.evaluator.osadl.CompatibilityMatrix

val ruleSet = ruleSet(ortResult, licenseInfoResolver) {
    val licenseView = LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED

    // Define a rule that is executed for each dependency.
    dependencyRule("OSADL_MATRIX_COMPATIBILITY") {
        // Requirements for the rule to trigger a violation.
        require {
            -isExcluded()
        }

        val projectLicenseInfo = licenseInfoResolver.resolveLicenseInfo(project.id).filter(licenseView)
        val outboundLicenses = projectLicenseInfo.licenses.map { it.license }

        // Define a rule that is executed for each license of the dependency.
        licenseRule("OSADL_PROJECT_LICENSE_COMPATIBILITY", licenseView) {
            outboundLicenses.forEach { outboundLicense ->
                val compatibilityInfo = CompatibilityMatrix
                    // Be conservative and use the simple license string without the exception string for lookup.
                    // Eventually resulting false-positives are better than an incompatibility to be missed.
                    .getCompatibilityInfo(outboundLicense.simpleLicense(), license.simpleLicense())

                if (compatibilityInfo.compatibility in Compatibility.COMPATIBLE_VALUES) return@forEach

                fun getSimplifiedLicenseString(license: SpdxSingleLicenseExpression) =
                    if (license.exception() != null) {
                        "${license.simpleLicense()} (simplified from '$license')"
                    } else {
                        "$license"
                    }

                val outboundLicenseString = getSimplifiedLicenseString(outboundLicense)
                val inboundLicenseString = getSimplifiedLicenseString(license)

                val projectCoords = project.id.toCoordinates()
                val depCoords = dependency.id.toCoordinates()

                when (compatibilityInfo.compatibility) {
                    Compatibility.CONTEXTUAL -> warning(
                        message = "Whether the outbound license $outboundLicenseString of project '$projectCoords' " +
                                "is compatible with the inbound license $inboundLicenseString of its dependency " +
                                "'$depCoords' depends on the context. ${compatibilityInfo.explanation}",
                        howToFix = "Get legal advice and eventually create a (global) rule violation resolution."
                    )

                    Compatibility.UNKNOWN -> warning(
                        message = "It is unknown whether the outbound license $outboundLicenseString of project " +
                                "'$projectCoords' is compatible with the inbound license $inboundLicenseString of " +
                                "its dependency '$depCoords'. ${compatibilityInfo.explanation}",
                        howToFix = "Get legal advice and eventually create a (global) rule violation resolution."
                    )

                    else -> error(
                        message = "The outbound license $outboundLicenseString of project '$projectCoords' is " +
                                "incompatible with the inbound license $inboundLicenseString of its dependency " +
                                "'$depCoords'. ${compatibilityInfo.explanation}",
                        howToFix = "Remove the dependency on '$depCoords' or put '$projectCoords' under a different " +
                                "license."
                    )
                }
            }
        }
    }
}

// Populate the list of errors to return.
ruleViolations += ruleSet.violations
