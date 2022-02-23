/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import org.ossreviewtoolkit.analyzer.managers.SPM.LibraryDependency
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageLinkage.DYNAMIC
import org.ossreviewtoolkit.model.utils.DependencyHandler

/**
 * A specialized [DependencyHandler] implementation for SPM.
 */
class SPMDependencyHandler : DependencyHandler<LibraryDependency> {

    override fun identifierFor(dependency: LibraryDependency): Identifier = dependency.getIdentifier()

    override fun dependenciesFor(dependency: LibraryDependency): Collection<LibraryDependency> = dependency.dependencies

    override fun linkageFor(dependency: LibraryDependency): PackageLinkage = DYNAMIC

    override fun createPackage(dependency: LibraryDependency, issues: MutableList<OrtIssue>): Package {
        return dependency.toPackage()
    }
}
