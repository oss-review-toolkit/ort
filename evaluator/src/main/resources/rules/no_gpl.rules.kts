/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

// Use this rule like:
//
// $ ort evaluate -i scanner/src/funTest/assets/dummy-expected-output-for-analyzer-result.yml --rules-resource /rules/no_gpl.rules.kts

// Define a custom rule matcher.
fun PackageRule.LicenseRule.isGpl() =
    object : RuleMatcher {
        override val description = "isGpl($license)"

        override fun matches() = "GPL" in license.toString()
    }

// Define the rule set.
val ruleSet = ruleSet(ortResult, licenseInfoResolver) {
    // Define a rule that is executed for each package.
    packageRule("NO_GPL") {
        // Define a rule that is executed for each license of the package.
        licenseRule("NO_GPL", LicenseView.ALL) {
            require {
                +isGpl()
            }

            error(
                "The package '${pkg.id.toCoordinates()}' has the ${licenseSource.name} license '$license'.",
                "Remove the dependency on this package."
            )
        }
    }
}

// Populate the list of errors to return.
ruleViolations += ruleSet.violations
