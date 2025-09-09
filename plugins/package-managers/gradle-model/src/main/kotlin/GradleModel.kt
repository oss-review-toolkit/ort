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

// As it is not possible to declare a package in "init.gradle" also no package is declared here.

// The following interfaces have to match those in "plugins/package-managers/gradle/src/main/resources/init.gradle"
// because they are used to deserialize the model produced there.

interface OrtDependencyTreeModel {
    val group: String
    val name: String
    val version: String
    val configurations: List<OrtConfiguration>
    val repositories: List<OrtRepository>
    val errors: List<String>
    val warnings: List<String>
}

interface OrtConfiguration {
    val name: String
    val dependencies: List<OrtDependency>
}

interface OrtDependency {
    val groupId: String
    val artifactId: String
    val version: String
    val classifier: String
    val extension: String
    val variants: Set<String>
    val dependencies: List<OrtDependency>
    val error: String?
    val warning: String?
    val pomFile: String?
    val mavenModel: OrtMavenModel?
    val localPath: String?
}

interface OrtMavenModel {
    val licenses: Set<String>
    val authors: Set<String>
    val description: String?
    val homepageUrl: String?
    val vcs: OrtVcsModel?
}

interface OrtVcsModel {
    val connection: String
    val tag: String
    val browsableUrl: String
}

interface OrtRepository {
    val url: String
    val username: String?
    val password: String?
}
