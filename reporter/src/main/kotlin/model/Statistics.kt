/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package com.here.ort.reporter.model

import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.Project
import com.here.ort.model.RuleViolation
import com.here.ort.model.Scope
import com.here.ort.model.Severity

import java.util.SortedSet

/**
 * A class containing statistics for an [OrtResult].
 */
data class Statistics(
    /**
     * The number of [OrtIssue]s by severity which are not resolved and not excluded.
     */
    val openIssues: IssueStatistics,

    /**
     * The number of [RuleViolation]s by severity which are not resolved.
     */
    val openRuleViolations: IssueStatistics,

    /**
     * Statistics for the dependency tree.
     */
    val dependencyTree: DependencyTreeStatistics
)

/**
 * A class containing the amount of issues per severity.
 */
data class IssueStatistics(
    /**
     * The number of issues with [Severity.ERROR] as severity.
     */
    val errors: Int,

    /**
     * The number of issues with [Severity.WARNING] as severity.
     */
    val warnings: Int,

    /**
     * The number of issues with [Severity.HINT] as severity.
     */
    val hints: Int
)

/**
 * A class containing statistics about the dependency trees.
 */
data class DependencyTreeStatistics(
    /**
     * The number of included [Project]s.
     */
    val includedProjects: Int,

    /**
     * The number of excluded [Project]s.
     */
    val excludedProjects: Int,

    /**
     * The number of included [Package]s.
     */
    val includedPackages: Int,

    /**
     * The number of excluded [Package]s.
     */
    val excludesPackages: Int,

    /**
     * The total depth of the deepest tree in the forest.
     */
    val totalTreeDepth: Int,

    /**
     * The depth of the deepest tree in the forest disregarding excluded tree nodes.
     */
    val includedTreeDepth: Int,

    /**
     * The set of scope names which have at least one not excluded corresponding [Scope].
     */
    val includedScopes: SortedSet<String>,

    /**
     * The set of scope names which do not have a single not excluded corresponding [Scope].
     */
    val excludedScopes: SortedSet<String>
)
