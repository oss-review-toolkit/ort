/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.evaluatedmodel

/**
 * The path to a [dependency][pkg] used by the [EvaluatedModel]. It is defined by the [project] and [scope] that contain
 * the dependency and the [list of parents][path] in the dependency tree.
 */
data class EvaluatedPackagePath(
    val pkg: EvaluatedPackage,
    val project: EvaluatedPackage,
    val scope: EvaluatedScope,
    val path: List<EvaluatedPackage>
)
