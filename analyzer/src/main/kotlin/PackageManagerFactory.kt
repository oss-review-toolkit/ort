/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer

import java.nio.file.FileSystems

/**
 * A factory to create new instances of [T]. It also stores some static information about the [PackageManager] type it
 * creates.
 *
 * @property homepageUrl The URL to the package manager's homepage.
 * @property primaryLanguage The name of the programming language this package manager is primarily used with.
 * @param globsForDefinitionFiles A prioritized list of glob patterns of definition files supported by this package
 *                                manager. Only all matches of the first glob having any matches is considered.
 */
abstract class PackageManagerFactory<out T : PackageManager>(
        val homepageUrl: String,
        val primaryLanguage: String,
        globsForDefinitionFiles: List<String>
) {
    /**
     * Create a new instance of the [PackageManager].
     */
    abstract fun create(): T

    /**
     * Return the Java class name to make JCommander display a proper name in list parameters of this custom type.
     */
    override fun toString() =
            javaClass.name.substringBefore('$').substringAfterLast('.')

    /**
     * The glob matchers for all definition files.
     */
    val matchersForDefinitionFiles = globsForDefinitionFiles.map {
        FileSystems.getDefault().getPathMatcher("glob:**/" + it)
    }
}
