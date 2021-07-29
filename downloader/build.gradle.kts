/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

val jgitVersion: String by project
val jSchAgentProxyVersion: String by project
val mockkVersion: String by project
val svnkitVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api(project(":model"))

    implementation(project(":utils"))

    implementation("com.jcraft:jsch.agentproxy.jsch:$jSchAgentProxyVersion")

    // Force the generated Maven POM to use the same version of "jsch" Gradle resolves the version conflict to.
    implementation("com.jcraft:jsch") {
        version {
            strictly("0.1.55")
        }
    }

    implementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.jsch:$jgitVersion")
    implementation("org.tmatesoft.svnkit:svnkit:$svnkitVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
}
