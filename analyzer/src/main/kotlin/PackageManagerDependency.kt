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

package org.ossreviewtoolkit.analyzer

import kotlin.contracts.contract

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Project

/**
 * A class to model a [linkage]-type of dependency on the results of another [packageManager] when resolving [scope]
 * from [definitionFile].
 */
data class PackageManagerDependency(
    val packageManager: String,
    val definitionFile: String,
    val scope: String,
    val linkage: PackageLinkage
) {
    fun findProjects(analyzerResult: AnalyzerResult): List<Project> =
        analyzerResult.projects.filter { it.definitionFilePath == definitionFile }.also { projects ->
            if (projects.isEmpty()) {
                logger.warn { "Could not find any project for definition file '$definitionFile'." }
            }

            projects.forEach { verify(it) }
        }

    fun verify(project: Project?) {
        contract {
            returns() implies (project != null)
        }

        requireNotNull(project) {
            "Could not find a project for the definition file '$definitionFile'."
        }

        requireNotNull(project.scopeNames) {
            "The project '${project.id.toCoordinates()}' from definition file '$definitionFile' does not use a " +
                "dependency graph."
        }

        if (scope !in project.scopeNames.orEmpty()) {
            logger.warn {
                "The project '${project.id.toCoordinates()}' from definition file '$definitionFile' does not contain " +
                    "the requested scope '$scope'."
            }
        }
    }
}
