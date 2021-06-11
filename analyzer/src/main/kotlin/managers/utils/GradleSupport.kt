/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

// As it is not possible to declare a package in "init.gradle" also no package is declared here.

// The following three interfaces have to match the interfaces defined in "analyzer/src/resources/init.gradle" because
// they are used to deserialize the model produced there.

interface DependencyTreeModel {
    val group: String
    val name: String
    val version: String
    val configurations: List<Configuration>
    val repositories: List<String>
    val errors: List<String>
    val warnings: List<String>
}

interface Configuration {
    val name: String
    val dependencies: List<Dependency>
}

interface Dependency {
    val groupId: String
    val artifactId: String
    val version: String
    val classifier: String
    val extension: String
    val dependencies: List<Dependency>
    val error: String?
    val warning: String?
    val pomFile: String?
    val localPath: String?
}
