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

import ch.frankel.slf4k.*

import com.here.ort.model.Identifier
import com.here.ort.model.LicenseSource
import com.here.ort.model.OrtIssue
import com.here.ort.model.RuleViolation
import com.here.ort.model.Severity
import com.here.ort.utils.log

/**
 * The base class for an evaluator rule. Rules use a set of matchers to determine if they apply, and create a list of
 * issues which are added to the [ruleSet].
 */
abstract class Rule(
    /**
     * The [RuleSet] this rule belongs to.
     */
    val ruleSet: RuleSet,

    /**
     * The name of this rule.
     */
    val name: String
) {
    private val ruleMatcherManager = RuleMatcherManager()

    /**
     * The list of all matchers that need to match for this rule to apply.
     */
    val matchers = mutableListOf<RuleMatcher>()

    /**
     * The list of all issues created by this rule.
     */
    val violations = mutableListOf<RuleViolation>()

    /**
     * Return a human-readable description of this rule.
     */
    abstract val description: String

    /**
     * Return true if all [matchers] match.
     */
    private fun matches() = matchers.all { matcher ->
        matcher.matches().also { matches ->
            log.info { "\t${matcher.description} == $matches" }
            if (!matches) log.info { "\tRule skipped." }
        }
    }

    /**
     * Evaluate this rule by checking if all matchers apply. If this is the case all [issues] configured in this rule
     * are added to the [ruleSet]. To add custom behavior if the rule matches override [runInternal].
     */
    fun evaluate() {
        log.info { description }

        if (matches()) {
            ruleSet.violations += violations

            if (violations.isNotEmpty()) {
                log.info {
                    "\tFound violations:\n\t\t${violations.joinToString("\n\t\t") { "${it.severity}: ${it.message}" }}"
                }
            }

            runInternal()
        }
    }

    /**
     * Can be overridden to implement custom behavior, executed if a rule [matches].
     */
    open fun runInternal() {}

    /**
     * DSL function to configure [RuleMatcher]s using a [RuleMatcherManager].
     */
    fun require(block: RuleMatcherManager.() -> Unit) {
        ruleMatcherManager.block()
    }

    /**
     * Return a string to be used as [source][OrtIssue.source] for issues generated in [hint], [warning], and [error].
     */
    abstract fun issueSource(): String

    fun issue(severity: Severity, pkgId: Identifier, license: String?, licenseSource: LicenseSource?, message: String) {
        violations += RuleViolation(
            severity = severity,
            rule = name,
            pkg = pkgId,
            license = license,
            licenseSource = licenseSource,
            message = message
        )
    }

    /**
     * Add a [hint][Severity.HINT] to the list of [issues].
     */
    fun hint(pkgId: Identifier, license: String?, licenseSource: LicenseSource?, message: String) = issue(Severity.HINT, pkgId, license, licenseSource, message)

    /**
     * Add a [warning][Severity.WARNING] to the list of [issues].
     */
    fun warning(pkgId: Identifier, license: String?, licenseSource: LicenseSource?, message: String) = issue(Severity.WARNING, pkgId, license, licenseSource, message)

    /**
     * Add an [error][Severity.ERROR] to the list of [issues].
     */
    fun error(pkgId: Identifier, license: String?, licenseSource: LicenseSource?, message: String) = issue(Severity.ERROR, pkgId, license, licenseSource, message)

    /**
     * A DSL helper class, providing convenience functions for adding [RuleMatcher]s to this rule.
     */
    inner class RuleMatcherManager {
        /**
         * Add a [RuleMatcher] to this rule.
         */
        operator fun RuleMatcher.unaryPlus() {
            matchers += this
        }

        /**
         * Add the negation of a [RuleMatcher] to this rule.
         */
        operator fun RuleMatcher.unaryMinus() {
            matchers += Not(this)
        }
    }
}
