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

import OrtConfiguration
import OrtDependency
import OrtDependencyTreeModel

import java.io.Serializable

@Suppress("SerialVersionUIDInSerializableClass")
class OrtDependencyTreeModelImpl(
    override val group: String,
    override val name: String,
    override val version: String,
    override val configurations: List<OrtConfiguration>,
    override val repositories: List<String>,
    override val errors: List<String>,
    override val warnings: List<String>
) : OrtDependencyTreeModel, Serializable

@Suppress("SerialVersionUIDInSerializableClass")
class OrtConfigurationImpl(
    override val name: String,
    override val dependencies: List<OrtDependency>
) : OrtConfiguration, Serializable

@Suppress("SerialVersionUIDInSerializableClass")
class OrtDependencyImpl(
    override val groupId: String,
    override val artifactId: String,
    override val version: String,
    override val classifier: String,
    override val extension: String,
    override val dependencies: List<OrtDependency>,
    override val error: String?,
    override val warning: String?,
    override val pomFile: String?,
    override val localPath: String?
) : OrtDependency, Serializable
