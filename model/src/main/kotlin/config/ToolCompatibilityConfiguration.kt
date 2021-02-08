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

package org.ossreviewtoolkit.model.config

/**
 * A data class storing properties that can be used to check the compatibility of a specific tool.
 *
 * Instances of this class are used to determine whether a specific tool in a concrete version is applicable in a
 * given context. Here typically the version is crucial. The class allows multiple ways to define the compatibility of
 * tool versions that are suitable for different version schemes used by tools:
 *
 *  - In the [semanticVersionSpec] field, a string conforming to NPM versioning rules can be specified (see
 *    https://github.com/npm/node-semver). This allows for complex expressions that determine ranges of semantic
 *    versions in a fine-granular way. Version ranges may also be used to define version bounds if the tool to be
 *    checked uses calendar versioning.
 *  - For tools making use of Calendar versioning, it may be desired to state that versions are accepted that are
 *    not older than a specific time range. This can be achieved by setting the [calendarVersionSpec] field to a
 *    string that is accepted by [java.time.Period.parse]. For instance, by setting this field to "P3M", declares that
 *    only tool versions not older than three months are valid.
 *  - If a tool uses a completely different versioning scheme, with [versionPattern] a regular expression can be
 *    specified that is matched against the version.
 *
 * By setting the properties accordingly, the compatibility check can be made very strict - e.g. by
 * setting a narrow version range -, or relaxed. Properties that are *null* always match; for instance, if the version
 * of a tool does not matter, the properties for the base version, the version bound, and the version delta can be set
 * to *null*.
 */
data class ToolCompatibilityConfiguration(
    /**
     * A regular expression pattern to match the tool name. This is typically the exact name of a specific tool; but
     * by specifying a regular expression, it is also possible to match multiple tools. (A *null* value would even
     * accept tools with arbitrary names.)
     */
    val namePattern: String? = null,

    /**
     * An optional version specification that is interpreted as an NPM version range; this is suitable for tools using
     * a semantic versioning scheme.
     */
    val semanticVersionSpec: String? = null,

    /**
     * An optional version specification to check against a calendar versioning string. This is interpreted as a
     * time range.
     */
    val calendarVersionSpec: String? = null,

    /**
     * An optional regular expression pattern to be matched against an arbitrary tool version.
     */
    val versionPattern: String? = null,
) {
    init {
        require(listOfNotNull(semanticVersionSpec, calendarVersionSpec, versionPattern).size <= 1) {
            "Only a single criterion to check version compatibility can be configured."
        }
    }
}
