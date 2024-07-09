/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils

import org.ossreviewtoolkit.model.Identifier

/**
 * Deduce an [Identifier] that has a namespace from the conventional NuGet package [name], using the given [type] and
 * [version] as-is. This function can be useful for third-party tools that use ORT as a library to be able to reproduce
 * a NuGet package [Identifier], e.g. to use it as part of curations.
 */
fun getIdentifierWithNamespace(type: String, name: String, version: String): Identifier {
    val components = name.split('.')
    val maxNamespaceComponents = (components.size - 1).coerceAtMost(2)

    var i = 0
    while (i < maxNamespaceComponents) {
        // If any namespace component start with a digit, treat all components as parts of the name.
        if (components[i].first().isDigit()) {
            i = 0
            break
        }

        ++i
    }

    val namespaceWithoutName = components.subList(0, i).joinToString(".")
    val nameWithoutNamespace = components.subList(i, components.size).joinToString(".")

    return Identifier(type, namespaceWithoutName, nameWithoutNamespace, version)
}
