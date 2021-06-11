/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import java.io.File

import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ProjectAnalyzerResult

/**
 * A data class representing the result of the execution of a [PackageManager]. An instance contains the single
 * results produced for the definition files the package manager supports. If there are global results (i.e. data that
 * is shared between the single project results), they are stored here as well.
 */
data class PackageManagerResult(
    /**
     * A map with the [ProjectAnalyzerResult]s created for the single definition files.
     */
    val projectResults: Map<File, List<ProjectAnalyzerResult>>,

    /**
     * An optional global [DependencyGraph]. If supported by the package manager, this graph contains all the
     * dependencies referenced by any of the processed definition files. This allows for a significant reduction of
     * redundancy in the dependency data.
     */
    val dependencyGraph: DependencyGraph? = null,

    /**
     * A set with [Package]s shared across the projects analyzed by this [PackageManager]. Package managers that
     * produce a shared [DependencyGraph] typically do not collect packages on a project-level, but globally. Such
     * packages can be stored in this property.
     */
    val sharedPackages: Set<Package> = sortedSetOf()
)
