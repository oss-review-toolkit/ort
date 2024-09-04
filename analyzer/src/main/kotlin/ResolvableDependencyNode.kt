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

import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.PackageLinkage

sealed class ResolvableDependencyNode : DependencyNode

class ProjectScopeDependencyNode(
    override val id: Identifier,
    override val linkage: PackageLinkage,
    override val issues: List<Issue>,
    private val dependencies: Sequence<DependencyNode>
) : ResolvableDependencyNode() {
    override fun <T> visitDependencies(block: (Sequence<DependencyNode>) -> T): T = block(dependencies)
}

class DependencyNodeDelegate(private val node: DependencyNode) : ResolvableDependencyNode() {
    override val id: Identifier = node.id
    override val linkage = node.linkage
    override val issues = node.issues
    override fun <T> visitDependencies(block: (Sequence<DependencyNode>) -> T): T = node.visitDependencies(block)
}
