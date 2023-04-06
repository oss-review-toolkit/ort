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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.internal.deprecation.DeprecatableConfiguration
import org.gradle.util.GradleVersion

/**
 * Return whether this Gradle configuration is relevant for ORT's dependency resolution.
 */
internal fun Configuration.isRelevant(): Boolean {
    val canBeResolved = GradleVersion.current() < GradleVersion.version("3.3") || isCanBeResolved

    val isDeprecatedConfiguration = GradleVersion.current() >= GradleVersion.version("6.0")
            && this is DeprecatableConfiguration && resolutionAlternatives != null

    return canBeResolved && !isDeprecatedConfiguration
}

/**
 * Return whether the Android plugin for Gradle has been applied to the project.
 */
internal fun Project.isAndroidProject(): Boolean =
    plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")
