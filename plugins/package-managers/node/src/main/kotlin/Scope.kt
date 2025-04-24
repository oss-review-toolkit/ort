/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import org.ossreviewtoolkit.model.config.Excludes

internal enum class Scope(val descriptor: String) {
    DEPENDENCIES("dependencies"),
    DEV_DEPENDENCIES("devDependencies");

    fun isExcluded(excludes: Excludes): Boolean = excludes.isScopeExcluded(descriptor)
}

internal fun Collection<Scope>.getNames(): Set<String> = mapTo(mutableSetOf()) { it.descriptor }

internal fun PackageJson.getDependenciesForScope(scope: Scope): Set<String> =
    when (scope) {
        Scope.DEPENDENCIES -> dependencies.keys + optionalDependencies.keys
        Scope.DEV_DEPENDENCIES -> devDependencies.keys
    }
