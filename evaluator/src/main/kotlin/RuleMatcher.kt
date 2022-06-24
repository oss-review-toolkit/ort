/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.evaluator

/**
 * A rule matcher, used to determine if a [Rule] applies.
 */
interface RuleMatcher {
    /**
     * A string describing this matcher.
     */
    val description: String

    /**
     * Return true if the rule applies, false otherwise.
     */
    fun matches(): Boolean
}

/**
 * A [RuleMatcher] that requires all provided [matchers] to match.
 */
class AllOf(private vararg val matchers: RuleMatcher) : RuleMatcher {
    override val description = "(${matchers.joinToString(" && ") { it.description }})"

    override fun matches() = matchers.all { it.matches() }
}

/**
 * A [RuleMatcher] that requires at least one of the provided [matchers] to match.
 */
class AnyOf(private vararg val matchers: RuleMatcher) : RuleMatcher {
    override val description = "(${matchers.joinToString(" || ") { it.description }})"

    override fun matches() = matchers.any { it.matches() }
}

/**
 * A [RuleMatcher] that requires none of the provided [matchers] to match.
 */
class NoneOf(private vararg val matchers: RuleMatcher) : RuleMatcher {
    override val description = "!(${matchers.joinToString(" || ") { it.description }})"

    override fun matches() = matchers.none { it.matches() }
}

/**
 * A [RuleMatcher] that inverts the result of the provided [matcher].
 */
class Not(private val matcher: RuleMatcher) : RuleMatcher {
    override val description = "!(${matcher.description})"

    override fun matches() = !matcher.matches()
}
