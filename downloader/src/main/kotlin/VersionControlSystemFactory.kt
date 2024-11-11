/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.downloader

import org.ossreviewtoolkit.utils.common.Plugin
import org.ossreviewtoolkit.utils.common.TypedConfigurablePluginFactory

/**
 * An abstract class to be implemented by factories for [version contral systems][VersionControlSystem].
 * The constructor parameter [type] denotes which VCS type is supported by this plugin.
 * The constructor parameter [priority] is used to determine the order in which the VCS plugins are used.
 */
abstract class VersionControlSystemFactory<CONFIG>(override val type: String, val priority: Int) :
    TypedConfigurablePluginFactory<CONFIG, VersionControlSystem> {
    companion object {
        /**
         * All [version control system factories][VersionControlSystemFactory] available in the classpath,
         * associated by their names, sorted by priority.
         */
        val ALL by lazy {
            Plugin.getAll<VersionControlSystemFactory<*>>()
                .toList()
                .sortedByDescending { (_, vcsFactory) -> vcsFactory.priority }
                .toMap()
        }
    }
}

/**
 * A base class for specific version control system configurations.
 */
open class VersionControlSystemConfiguration
