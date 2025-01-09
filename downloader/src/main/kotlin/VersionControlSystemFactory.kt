/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.util.ServiceLoader

import org.ossreviewtoolkit.utils.common.Plugin
import org.ossreviewtoolkit.utils.common.TypedConfigurablePluginFactory

/**
 * An abstract class to be implemented by factories for [Version Control Systems][VersionControlSystem] for use with the
 * [ServiceLoader] mechanism. The [type] parameter denotes which VCS type is supported by this plugin, while the
 * [priority] parameter defines the order if more than one plugin supports the same VCS type.
 */
abstract class VersionControlSystemFactory<CONFIG>(override val type: String, val priority: Int) :
    TypedConfigurablePluginFactory<CONFIG, VersionControlSystem> {
    companion object {
        /**
         * All [Version Control System factories][VersionControlSystemFactory] available in the classpath, associated by
         * their names, sorted by priority.
         */
        val ALL by lazy {
            Plugin.getAll<VersionControlSystemFactory<*>>()
                .toList()
                .sortedByDescending { (_, vcsFactory) -> vcsFactory.priority }
                .toMap()
        }
    }
}
