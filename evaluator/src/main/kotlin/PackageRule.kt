/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.evaluator

import com.here.ort.model.Identifier
import com.here.ort.model.LicenseFinding
import com.here.ort.model.Package
import com.here.ort.model.Project
import com.here.ort.model.config.Excludes
import com.here.ort.model.config.PathExclude
import com.here.ort.spdx.SpdxLicense

/**
 * A [Rule] to check a single [Package].
 */
open class PackageRule(
    ruleSet: RuleSet,
    name: String,

    /**
     * The [Package] to check.
     */
    val pkg: Package,

    /**
     * The detected licenses for the [Package].
     */
    val detectedLicenses: List<LicenseFinding>
) : Rule(ruleSet, name) {
    private val licenseRules = mutableListOf<LicenseRule>()

    override val description = "Evaluating rule '$name' for package '${pkg.id.toCoordinates()}'."

    override fun issueSource() = "$name - ${pkg.id.toCoordinates()}"

    override fun runInternal() {
        licenseRules.forEach { it.evaluate() }
    }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] has any concluded, declared, or detected license.
     */
    fun hasLicense() =
        object : RuleMatcher {
            override val description = "hasLicense()"

            override fun matches() =
                pkg.concludedLicense?.licenses()?.isNotEmpty() == true
                        || pkg.declaredLicenses.isNotEmpty()
                        || detectedLicenses.isNotEmpty()
        }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] is [excluded][Excludes].
     */
    fun isExcluded() =
        object : RuleMatcher {
            override val description = "isExcluded()"

            override fun matches() = ruleSet.ortResult.isExcluded(pkg.id)
        }

    /**
     * A [RuleMatcher] that checks if the [identifier][Package.id] of the [package][pkg] belongs to one of the provided
     * [orgs][Identifier.isFromOrg].
     */
    fun isFromOrg(vararg names: String) =
        object : RuleMatcher {
            override val description = "isFromOrg(${names.joinToString()})"

            override fun matches() = pkg.id.isFromOrg(*names)
        }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] was created from a [Project].
     */
    fun isProject() =
        object : RuleMatcher {
            override val description = "isProject()"

            override fun matches() = ruleSet.ortResult.analyzer?.result?.projects?.find { it.id == pkg.id } != null
        }

    /**
     * A [RuleMatcher] that checks if the [identifier type][Identifier.type] of the [package][pkg] equals [type].
     */
    fun isType(type: String) =
        object : RuleMatcher {
            override val description = "isType($type)"

            override fun matches() = pkg.id.type == type
        }

    /**
     * A DSL function to configure a [LicenseRule] and add it to this rule.
     */
    fun licenseRule(name: String, licenseView: LicenseView, block: LicenseRule.() -> Unit) {
        val licenses = licenseView.licenses(pkg, detectedLicenses.map { it.license })

        licenses.forEach { (license, licenseSource) ->
            val findings = if (licenseSource == LicenseSource.DETECTED) {
                ruleSet.licenseFindings[pkg.id].orEmpty()
            } else {
                emptyMap()
            }.filter { (finding, _) -> finding.license == license }

            licenseRules += LicenseRule(name, license, licenseSource, findings).apply(block)
        }
    }

    /**
     * A [Rule] to check a single license of the [package][pkg].
     */
    inner class LicenseRule(
        name: String,

        /**
         * The license to check.
         */
        val license: String,

        /**
         * The source of the license.
         */
        val licenseSource: LicenseSource,

        /**
         * The associated [LicenseFinding]s. Only used if [licenseSource] is [LicenseSource.DETECTED].
         */
        val licenseFindings: Map<LicenseFinding, List<PathExclude>> = emptyMap()
    ) : Rule(ruleSet, name) {
        /**
         * A helper function to access [PackageRule.pkg] in extension functions for [LicenseRule], required because the
         * properties of the outer class [PackageRule] cannot be accessed from an extension function.
         */
        fun pkg() = pkg

        /**
         * A helper function to access [PackageRule.detectedLicenses] in extension functions for [LicenseRule], required
         * because the properties of the outer class [PackageRule] cannot be accessed from an extension function.
         */
        fun detectedLicenses() = detectedLicenses

        override val description = "\tEvaluating license rule '$name' for $licenseSource license '$license'."

        override fun issueSource() = "$name - ${pkg.id.toCoordinates()} - $license ($licenseSource)"

        /**
         * A [RuleMatcher] that checks if a [detected][LicenseSource.DETECTED] license is excluded. This is the case if
         * all keys of [licenseFindings] are associated to at least one [PathExclude].
         */
        fun isExcluded() =
            object : RuleMatcher {
                override val description = "isExcluded($license)"

                override fun matches() =
                    licenseSource != LicenseSource.DETECTED || licenseFindings.values.all { it.isNotEmpty() }
            }

        /**
         * A [RuleMatcher] that checks if the [license] is a valid [SpdxLicense].
         */
        fun isSpdxLicense() =
            object : RuleMatcher {
                override val description = "isSpdxLicense($license)"

                override fun matches() = SpdxLicense.forId(license) != null
            }
    }
}
