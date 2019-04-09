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
import com.here.ort.model.config.Excludes
import com.here.ort.model.config.PathExclude

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

    override fun describeRule() = "Evaluating rule '$name' for package '${pkg.id.toCoordinates()}'."

    override fun run(): Boolean {
        if (super.run()) {
            return licenseRules.all { licenseRule ->
                licenseRule.run().also {
                    this@PackageRule.issues += licenseRule.issues
                }
            }
        }
        return false
    }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] has any concluded, declared, or detected license.
     */
    fun hasLicense() =
        object : RuleMatcher {
            override fun matches() =
                pkg.concludedLicense?.licenses()?.isNotEmpty() == true
                        || pkg.declaredLicenses.isNotEmpty()
                        || detectedLicenses.isNotEmpty()

            override fun describe() = "hasLicense()"
        }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] is [excluded][Excludes].
     */
    fun isExcluded() =
        object : RuleMatcher {
            override fun matches() = ruleSet.ortResult.isExcluded(pkg.id)

            // TODO: replace describeMismatch() with generated string, e.g. "`isExcluded() == false`"
            // the Not rule could then print "`not(isExcluded()) == false`"
            override fun describe() = "isExcluded()"
        }

    /**
     * A [RuleMatcher] that checks if the [identifier][Package.id] of this [package][pkg] belongs to one of the provided
     * [orgs][Identifier.isFromOrg].
     */
    fun isFromOrg(vararg names: String) =
        object : RuleMatcher {
            override fun matches() = pkg.id.isFromOrg(*names)

            override fun describe() = "isFromOrg(${names.joinToString()})"
        }

    /**
     * A [RuleMatcher] that checks if the [identifier type][Identifier.type] of this [package][pkg] equals [type].
     */
    fun isType(type: String) =
        object : RuleMatcher {
            override fun matches() = pkg.id.type == type

            override fun describe() = "isType($type)"
        }

    /**
     * DSL function to configure a [LicenseRule] and add it to this rule.
     */
    fun licenseRule(name: String, licenseView: LicenseView, block: LicenseRule.() -> Unit) {
        val licenses = licenseView.licenses(pkg, detectedLicenses.map { it.license })

        licenses.forEach { (license, licenseSource) ->
            val findings = if (licenseSource == LicenseSource.DETECTED) {
                ruleSet.ortResult.collectLicenseFindings()[pkg.id].orEmpty()
            } else {
                emptyMap()
            }

            val licenseRule = LicenseRule(name, license, licenseSource, findings)
            licenseRule.block()

            licenseRules += licenseRule
        }
    }

    /**
     * A [Rule] to check a single license of this [package][pkg].
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
        override fun describeRule() = "\tEvaluating license rule '$name' for $licenseSource license '$license'."
    }
}
