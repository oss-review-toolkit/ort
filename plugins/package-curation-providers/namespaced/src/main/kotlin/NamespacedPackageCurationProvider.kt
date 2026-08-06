/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.namespaced

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.fromYaml
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.common.FileMatcher

/**
 * A configuration entry mapping a package coordinate pattern to a [PackageCurationData] that should be applied to
 * all packages matching the pattern.
 */
private data class NamespaceCuration(
    /**
     * An Ant-style glob pattern used to match a package's full `<type>:<namespace>:<name>:<version>` coordinate
     * string. For details about the glob syntax see the [FileMatcher] implementation.
     */
    val pattern: String,

    /** The [PackageCurationData] to apply to packages matching [pattern]. */
    val curations: PackageCurationData
)

data class NamespacedPackageCurationProviderConfig(
    /**
     * A YAML string defining a list of namespace-based package curations as configured in `ort.yml`. Each entry has a
     * `namespace` key — an Ant-style glob pattern matched against a package's full
     * `<type>:<namespace>:<name>:<version>` coordinate string — and a `curations` key with the [PackageCurationData]
     * to apply to matching packages. For details about the glob syntax see the [FileMatcher] implementation. Use a
     * literal `:` to represent an empty namespace or name.
     */
    val curations: String
)

/**
 * A [PackageCurationProvider] that provides [PackageCuration]s for packages based on a user-defined mapping of
 * package coordinates to curation data. Each entry's `namespace` key is an Ant-style glob pattern matched against a
 * package's full `<type>:<namespace>:<name>:<version>` coordinate string. For each matching package, the configured
 * curation data is applied.
 */
@OrtPlugin(
    displayName = "Namespaced",
    summary = "A package curation provider that applies curations to packages based on their namespace.",
    factory = PackageCurationProviderFactory::class
)
class NamespacedPackageCurationProvider(
    override val descriptor: PluginDescriptor = NamespacedPackageCurationProviderFactory.descriptor,
    config: NamespacedPackageCurationProviderConfig
) : PackageCurationProvider {
    private val namespaceCurations: List<NamespaceCuration> = runCatching {
        config.curations.fromYaml<List<NamespaceCurationConfig>>()
    }.getOrElse {
        throw IllegalArgumentException("Failed to parse the 'curations' option as YAML.", it)
    }.map { entry ->
        require(entry.namespace.isNotBlank()) {
            "The namespace must not be blank."
        }

        NamespaceCuration(pattern = entry.namespace, curations = entry.curations)
    }

    init {
        require(namespaceCurations.isNotEmpty()) {
            "At least one namespace curation must be defined."
        }
    }

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> {
        val results = mutableSetOf<PackageCuration>()

        packages.forEach { pkg ->
            val curations = getCurationsForCoordinates(pkg.id.toCoordinates())
            if (curations != null && curations != PackageCurationData()) {
                results += PackageCuration(pkg.id, curations)
            }
        }

        return results
    }

    private fun getCurationsForCoordinates(pkgCoordinates: String): PackageCurationData? =
        namespaceCurations.firstNotNullOfOrNull { entry ->
            if (FileMatcher.matches(entry.pattern, pkgCoordinates)) entry.curations else null
        }
}

/**
 * The configuration format of a single namespace curation entry as it is read from the inline YAML option.
 */
private data class NamespaceCurationConfig(
    val namespace: String,
    val curations: PackageCurationData
)
