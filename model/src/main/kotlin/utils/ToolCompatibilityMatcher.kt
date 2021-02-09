/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.utils

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period

import org.ossreviewtoolkit.model.config.ToolCompatibilityConfiguration

/**
 * A class allowing to check the compatibility of specific tools based on a list of [ToolCompatibilityConfiguration]
 * objects.
 */
class ToolCompatibilityMatcher(
    /**
     * A list with configurations determining the compatibility of a number of tools.
     */
    val compatibilityConfigurations: List<ToolCompatibilityConfiguration>,

    /**
     * A reference date that is used for checks against a calendar version spec.
     */
    val referenceDate: LocalDate = LocalDate.now()
) {
    companion object {
        /**
         * A default [CompatibilityConfigurationCreator] that returns a configuration, which never matches a tool.
         * It is used as default for [matches], so that unknown tools are rejected.
         */
        val REJECT_UNKNOWN: CompatibilityConfigurationCreator = { nonMatchingConfiguration }

        /**
         * A default [CompatibilityConfigurationCreator] that returns a configuration, which accepts all tools. It can
         * be passed to [matches] to state that tools not explicitly listed in the configuration should be accepted by
         * default.
         */
        val ACCEPT_UNKNOWN: CompatibilityConfigurationCreator = { allMatchingConfiguration }

        /**
         * A configuration instance that never matches a tool because of an empty version range. This is used to
         * reject all unknown tools.
         */
        private val nonMatchingConfiguration = ToolCompatibilityConfiguration(semanticVersionSpec = ">2 <1")

        /**
         * A configuration instance that matches all tools as it does not define any matching criteria. This can be
         * used to accept all unknown tools.
         */
        private val allMatchingConfiguration = ToolCompatibilityConfiguration()
    }

    /** Holds the actual data to determine the compatibility of tools. */
    private val matchingData = generateMatchingData(compatibilityConfigurations, referenceDate)

    /**
     * Test whether the tool with the properties specified is compatible according to the compatibility configurations
     * contained in this instance. The function finds the first configuration that matches the given [toolName]. If
     * found, the properties of this configuration are tested against the provided [toolVersion], to determine the
     * compatibility of the tool. If no configuration is found that applies to the [toolName], one is requested from
     * the [defaultConfigCreator] and matched against the tool's properties; so this configuration determines how to
     * deal with tools that are not listed explicitly in the compatibility configuration.
     */
    fun matches(
        toolName: String,
        toolVersion: String,
        defaultConfigCreator: CompatibilityConfigurationCreator = REJECT_UNKNOWN
    ): Boolean {
        val toolData = matchingData.filter { it.matchesTool(toolName) }.takeUnless { it.isEmpty() }
            ?: defaultMatchingData(toolName, defaultConfigCreator)

        return toolData.any { it.versionMatcher(toolVersion) }
    }

    /**
     * Return a (possibly empty) list with matching data objects obtained from [defaultConfigCreator] that are
     * applicable for [toolName]. This function is called if the matcher does not have any information about the
     * current tool to be matched. Then configuration is requested from the [defaultConfigCreator] and checked whether
     * it matches the tool.
     */
    private fun defaultMatchingData(
        toolName: String,
        defaultConfigCreator: CompatibilityConfigurationCreator
    ): List<ToolMatchingData> =
        listOfNotNull(defaultConfigCreator().toMatchingData(referenceDate).takeIf { it.matchesTool(toolName) })
}

/**
 * Type alias of a function that creates a [ToolCompatibilityConfiguration]. This function is used by
 * [ToolCompatibilityMatcher] to construct a [ToolCompatibilityConfiguration] lazily if no configuration is found for
 * a specific tool. The configuration produced by this function than determines the default behavior for
 * compatibility checks.
 */
typealias CompatibilityConfigurationCreator = () -> ToolCompatibilityConfiguration

/**
 * Type alias of a function that performs a version check for a tool version. The function is passed the current tool
 * version and returns a flag whether this version is accepted.
 */
private typealias VersionMatcher = (String) -> Boolean

/**
 * An internally used data class holding information that allows matching tools more efficiently. Instances are
 * derived from [ToolCompatibilityConfiguration] objects.
 */
private data class ToolMatchingData(
    /** The original configuration this instance was derived from. */
    val configuration: ToolCompatibilityConfiguration,

    /** The regular expression pattern to match the tool name. */
    val namePattern: Regex?,

    /** A function to effectively check the compatibility of a tool version. */
    val versionMatcher: VersionMatcher
) {
    /**
     * Test whether this instance applies to the given [toolName].
     */
    fun matchesTool(toolName: String): Boolean = namePattern?.matches(toolName) ?: true
}

/**
 * Generate a list of [ToolMatchingData] objects from the given [configurations] and the [referenceDate]. The objects
 * in this list contain processed information allowing for more efficient tests.
 */
private fun generateMatchingData(configurations: List<ToolCompatibilityConfiguration>, referenceDate: LocalDate):
        List<ToolMatchingData> = configurations.map { it.toMatchingData(referenceDate) }

/**
 * Generate a [ToolMatchingData] object for this configuration taking the various settings into account. In case of
 * calendar versioning, check against the given [referenceDate].
 */
private fun ToolCompatibilityConfiguration.toMatchingData(referenceDate: LocalDate): ToolMatchingData =
    ToolMatchingData(this, namePattern?.let { Regex(it) }, versionMatcher(referenceDate))

/**
 * Create a [VersionMatcher] for the version-related properties in this configuration and the given [referenceDate].
 * Depending on the concrete properties that are defined, different matcher functions are returned.
 */
private fun ToolCompatibilityConfiguration.versionMatcher(referenceDate: LocalDate): VersionMatcher =
    semanticVersionMatcher() ?: calendarVersionMatcher(referenceDate) ?: versionPatternMatcher() ?: { true }

/**
 * Create a [VersionMatcher] that checks against a semantic version spec.
 */
private fun ToolCompatibilityConfiguration.semanticVersionMatcher(): VersionMatcher? =
    semanticVersionSpec?.let {
        val requirement = Requirement.buildNPM(it)
        requirementMatcher(requirement)
    }

/**
 * Create a [VersionMatcher] that checks against a range derived from a calendar version spec. The spec is interpreted
 * as a period that determines the oldest version date of a tool to be considered as valid. Based on this lower
 * bound, a version range is constructed.
 */
private fun ToolCompatibilityConfiguration.calendarVersionMatcher(referenceDate: LocalDate): VersionMatcher? =
    calendarVersionSpec?.let {
        val period = Period.parse(calendarVersionSpec)
        val startDate = referenceDate.minus(period)
        dateRangeMatcher(startDate, referenceDate)
    }

/**
 * Create a [VersionMatcher] that checks an arbitrary version string against a regular expression pattern.
 */
private fun ToolCompatibilityConfiguration.versionPatternMatcher(): VersionMatcher? =
    versionPattern?.let { patternMatcher(Regex(it)) }

/**
 * Return a [VersionMatcher] that tries to match a version against the given [requirement]. The current version is
 * interpreted as a semantic version; if this is not possible, the check fails.
 */
private fun requirementMatcher(requirement: Requirement): VersionMatcher =
    { version ->
        try {
            Semver(version, Semver.SemverType.NPM).satisfies(requirement)
        } catch (e: SemverException) {
            false
        }
    }

/**
 * Return a [VersionMatcher] that checks whether a calendar version is withing the given [startDate] and [endDate].
 * If the version cannot be converted to a valid calendar date, the check fails.
 * TODO: This check could be implemented using the [Requirement] class, similar to the checks for semantic versions;
 * however, due to a bug in the [Semver] class, this is currently not possible. Refer to
 * https://github.com/vdurmont/semver4j/issues/53.
 */
private fun dateRangeMatcher(startDate: LocalDate, endDate: LocalDate): VersionMatcher =
    { version ->
        try {
            val semver = Semver(version)
            val versionDate = LocalDate.of(semver.major, semver.minor, semver.patch)
            (startDate.isBefore(versionDate) || startDate == versionDate) &&
                    (endDate.isAfter(versionDate) || endDate == versionDate)
        } catch (e: DateTimeException) {
            false
        } catch (e: SemverException) {
            false
        }
    }

/**
 * Return a [VersionMatcher] that checks a version string against the given [pattern].
 */
private fun patternMatcher(pattern: Regex): VersionMatcher = { version -> pattern.matches(version) }
