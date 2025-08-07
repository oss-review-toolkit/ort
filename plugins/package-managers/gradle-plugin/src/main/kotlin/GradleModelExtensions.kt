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

package org.ossreviewtoolkit.plugins.packagemanagers.gradleplugin

import OrtRepository

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.attributes.AttributeContainer
import org.gradle.internal.deprecation.DeprecatableConfiguration
import org.gradle.util.GradleVersion

/**
 * Look up an attribute by its name irrespective of the type and return its value, or return null if there is no such
 * attribute.
 */
internal fun AttributeContainer.getValueByName(name: String): Any? =
    attributes.keySet().find { it.name == name }?.let { getAttribute(it) }

/**
 * Return whether this Gradle configuration is relevant for ORT's dependency resolution.
 */
internal fun Configuration.isRelevant(): Boolean {
    // Configurations can be resolved and / or consumed, but not neither, see the table at
    // https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:resolvable-consumable-configs
    val canBeResolved = GradleVersion.current() < GradleVersion.version("3.3") || isCanBeResolved

    // Check if a configuration is deprecated in favor of alternatives.
    val isDeprecatedConfiguration = GradleVersion.current() >= GradleVersion.version("6.0")
        && this is DeprecatableConfiguration && !resolutionAlternatives.isNullOrEmpty()

    // Do not try to resolve dependencies metadata configurations as there often cause failures with Gradle itself. See
    // https://developer.android.com/build/releases/past-releases/agp-4-0-0-release-notes#dependency-metadata and
    // https://gist.github.com/h0tk3y/41c73d1f822378f52f1e6cce8dcf56aa for some background information.
    val isDependenciesMetadata = name.endsWith("DependenciesMetadata")

    // Ignore Kotlin Multiplatform Project configurations for resolving source files because by nature not every
    // published library has sources variants that can be resolved.
    val isDependencySources = name == "dependencySources" || name.endsWith("DependencySources")

    return canBeResolved && !isDeprecatedConfiguration && !isDependenciesMetadata && !isDependencySources
}

/**
 * Return a map that associates names of artifact repositories to their model representations.
 */
internal fun RepositoryHandler.associateNamesWithUrlsTo(repositories: MutableMap<String, UrlArtifactRepository>) =
    filterIsInstance<UrlArtifactRepository>().associateByTo(repositories) { (it as ArtifactRepository).name }

/**
 * Convert this [UrlArtifactRepository] to an [OrtRepository] by extracting the relevant properties.
 */
internal fun UrlArtifactRepository.toOrtRepository(): OrtRepository {
    val credentials = (this as? AuthenticationSupported)?.credentials
    return OrtRepositoryImpl(
        url = url.toString(),
        username = credentials?.username,
        password = credentials?.password
    )
}
