/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.model.utils

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.common.equalsOrIsBlank
import org.ossreviewtoolkit.utils.ort.showStackTrace

/** Return true if the version of this [org.ossreviewtoolkit.model.Identifier] is an Ivy version range. */
fun Identifier.hasIvyVersionRange(): Boolean = version.isIvyVersionRange()

/**
 * Return true if the version of this [Identifier] interpreted as an Ivy version matcher is applicable to the
 * package with the given [identifier][pkgId].
 */
private fun Identifier.isApplicableIvyVersion(pkgId: Identifier): Boolean =
    runCatching {
        version.containsIvyVersion(pkgId.version)
    }.onFailure {
        logger.warn {
            "Failed to check if identifier version '$version' is applicable to package version " +
                "'${pkgId.version}' of package '${pkgId.toCoordinates()}'."
        }

        it.showStackTrace()
    }.getOrDefault(false)

/**
 * Return true if this [Identifier] is matched by the given [matcher]. The [matcher]s version may be an
 * [Ivy version matcher](http://ant.apache.org/ivy/history/2.4.0/settings/version-matchers.html). If [blankNameMatchAny]
 * is true, a blank [matcher.name] matches any [name].
 */
internal fun Identifier.isMatchedBy(matcher: Identifier, blankNameMatchAny: Boolean = true): Boolean =
    type.equals(matcher.type, ignoreCase = true)
        && namespace == matcher.namespace
        && ((blankNameMatchAny && matcher.name.isBlank()) || name == matcher.name)
        && (matcher.version.equalsOrIsBlank(version) || matcher.isApplicableIvyVersion(this))
